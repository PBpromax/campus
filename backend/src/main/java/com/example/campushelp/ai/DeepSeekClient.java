package com.example.campushelp.ai;

import com.fasterxml.jackson.databind.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;

@Component
public class DeepSeekClient {
    private final ObjectMapper json;
    private final String key, model, base;
    private final long deadlineMs;
    private final ScheduledExecutorService deadlines=Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon(true).name("model-deadline").factory());
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();
    @Autowired public DeepSeekClient(ObjectMapper json, @Value("${deepseek.api-key:}") String key,
        @Value("${deepseek.model:deepseek-flash}") String model,
        @Value("${deepseek.base-url:https://api.deepseek.com}") String base,
        @Value("${deepseek.deadline-ms:45000}") long deadlineMs) {
        if(deadlineMs<100 || deadlineMs>50000) throw new IllegalArgumentException("Model deadline must be 100..50000 ms");
        this.json=json;this.key=key;this.model=model;this.base=base;this.deadlineMs=deadlineMs;
    }
    public DeepSeekClient(ObjectMapper json,String key,String model,String base) {this(json,key,model,base,45000);}
    @PreDestroy public void close() {deadlines.shutdownNow();http.shutdownNow();}
    public String model() { return model; }
    public boolean configured() { return !key.isBlank(); }
    public static class Cancellation {
        private final AtomicReference<ModelFailure.Kind> reason=new AtomicReference<>();
        private InputStream input;private CompletableFuture<?> pending;
        public synchronized void attach(InputStream input) throws IOException {this.input=input;if(cancelled()) input.close();}
        public synchronized void attach(CompletableFuture<?> pending) {this.pending=pending;if(cancelled()) pending.cancel(true);}
        private synchronized void stop(ModelFailure.Kind kind) {
            if(!reason.compareAndSet(null,kind)) return;
            if(pending!=null) pending.cancel(true);
            try {if(input!=null) input.close();}catch(IOException ignored) {}
        }
        public void cancel() {stop(ModelFailure.Kind.CLIENT_CANCELLED);}
        void timeout() {stop(ModelFailure.Kind.TIMEOUT);}
        public boolean cancelled() {return reason.get()!=null;}
        public boolean timedOut() {return reason.get()==ModelFailure.Kind.TIMEOUT;}
        ModelFailure failure() {return new ModelFailure(reason.get()==null?ModelFailure.Kind.NETWORK:reason.get());}
    }
    public record Usage(long promptTokens,long completionTokens,long totalTokens,Long cacheHitTokens,Long cacheMissTokens) {}
    public record Reply(String text,String arguments,String toolName,Usage usage) {
        public Reply(String text,String arguments,String toolName) {this(text,arguments,toolName,null);}
        public Reply(String text,String arguments) {this(text,arguments,arguments.isBlank()?"":"propose_draft",null);}
    }
    private Usage usage(JsonNode node) {
        if(!node.isObject()) return null;
        Long prompt=nonNegative(node.get("prompt_tokens")),completion=nonNegative(node.get("completion_tokens")),total=nonNegative(node.get("total_tokens"));
        if(prompt==null || completion==null || total==null || prompt>Long.MAX_VALUE-completion || prompt+completion!=total) return null;
        Long hit=nonNegative(node.get("prompt_cache_hit_tokens")),miss=nonNegative(node.get("prompt_cache_miss_tokens"));
        if(hit!=null && hit>prompt) hit=null;
        if(miss!=null && miss>prompt) miss=null;
        if(hit!=null && miss!=null && hit!=prompt-miss) {hit=null;miss=null;}
        return new Usage(prompt,completion,total,hit,miss);
    }
    private Long nonNegative(JsonNode node) { return node!=null && node.isIntegralNumber() && node.canConvertToLong() && node.longValue()>=0?node.longValue():null; }
    public Reply stream(List<Map<String,Object>> messages,Map<String,Object> tool, Consumer<String> delta, Cancellation cancellation) throws Exception {
        if(cancellation.cancelled()) throw cancellation.failure();
        if(!configured()) throw new ModelFailure(ModelFailure.Kind.UPSTREAM_AUTH);
        String serialized=json.writeValueAsString(payload(messages,tool));
        if(serialized.getBytes(StandardCharsets.UTF_8).length>ModelControls.MAX_PAYLOAD_BYTES) throw new ModelFailure(ModelFailure.Kind.UPSTREAM_REJECTED);
        var request=HttpRequest.newBuilder(URI.create(base+"/chat/completions")).timeout(Duration.ofMillis(deadlineMs))
            .header("Authorization","Bearer "+key).header("Content-Type","application/json")
            .POST(HttpRequest.BodyPublishers.ofString(serialized)).build();
        var timer=deadlines.schedule(cancellation::timeout,deadlineMs,TimeUnit.MILLISECONDS);
        try {
            var pending=http.sendAsync(request,HttpResponse.BodyHandlers.ofInputStream());cancellation.attach(pending);
            var response=pending.get();cancellation.attach(response.body());
            try(var reader=new BufferedReader(new InputStreamReader(new LimitedInput(response.body()),StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)))) {
                int status=response.statusCode();
                if(status!=200) {
                    ModelFailure.Kind kind=status==429?ModelFailure.Kind.UPSTREAM_RATE_LIMIT:(status==401 || status==403)?ModelFailure.Kind.UPSTREAM_AUTH:
                        status>=500?ModelFailure.Kind.UPSTREAM_5XX:ModelFailure.Kind.UPSTREAM_REJECTED;
                    throw new ModelFailure(kind);
                }
            StringBuilder text=new StringBuilder(),args=new StringBuilder(),name=new StringBuilder();
            var answer=new AnswerText();
            Consumer<String> visible=part->{text.append(part);delta.accept(part);};
            int rawTextSize=0;
            int size=0;boolean done=false;String finish="";Usage usage=null;
            for(String line;(line=reader.readLine())!=null;) {
                if(cancellation.cancelled()) throw cancellation.failure();
                size+=line.length();if(size>200000) throw new ModelFailure(ModelFailure.Kind.INVALID_RESPONSE);
                if(!line.startsWith("data:")) continue;
                String data=line.substring(5).strip();
                if(data.equals("[DONE]")) { done=true;break; }
                if(data.isEmpty()) continue;
                JsonNode chunk=json.readTree(data);
                if(chunk.has("error")) throw new ModelFailure(ModelFailure.Kind.INVALID_RESPONSE);
                if(chunk.hasNonNull("usage")) usage=usage(chunk.get("usage"));
                for(JsonNode choice:chunk.path("choices")) {
                    if(!choice.path("finish_reason").isNull() && choice.has("finish_reason")) finish=choice.path("finish_reason").asText();
                    var d=choice.path("delta");
                    if(d.path("content").isTextual()) {
                        String part=d.get("content").asText();rawTextSize+=part.length();
                        if(rawTextSize>4000) throw new ModelFailure(ModelFailure.Kind.INVALID_RESPONSE);
                        answer.append(part,visible);
                    }
                    for(var call:d.path("tool_calls")) {
                        if(call.path("index").asInt(-1)!=0) throw new ModelFailure(ModelFailure.Kind.INVALID_RESPONSE);
                        var f=call.path("function");name.append(f.path("name").asText(""));args.append(f.path("arguments").asText(""));
                        if(args.length()>24000) throw new ModelFailure(ModelFailure.Kind.INVALID_RESPONSE);
                    }
                }
            }
            answer.finish(visible);
            if(!done || !Set.of("stop","tool_calls").contains(finish)) throw new ModelFailure(ModelFailure.Kind.INVALID_RESPONSE);
            if(!name.isEmpty() && !Set.of("propose_draft","suggest_action").contains(name.toString())) throw new ModelFailure(ModelFailure.Kind.INVALID_RESPONSE);
            if(name.isEmpty() && !args.isEmpty()) throw new ModelFailure(ModelFailure.Kind.INVALID_RESPONSE);
            if(text.isEmpty() && args.isEmpty()) throw new ModelFailure(ModelFailure.Kind.INVALID_RESPONSE);
            if(cancellation.cancelled()) throw cancellation.failure();
            return new Reply(text.toString(),args.toString(),name.toString(),usage);
            }
        } catch(Exception error) {
            if(cancellation.cancelled()) throw cancellation.failure();
            if(error instanceof ModelFailure failure) throw failure;
            if(error instanceof InterruptedException) {Thread.currentThread().interrupt();throw new ModelFailure(ModelFailure.Kind.CLIENT_CANCELLED);}
            if(error instanceof ExecutionException wrapped && wrapped.getCause() instanceof HttpTimeoutException) throw new ModelFailure(ModelFailure.Kind.TIMEOUT);
            if(error instanceof ExecutionException || (error instanceof IOException && !(error instanceof java.nio.charset.CharacterCodingException) && !(error instanceof com.fasterxml.jackson.core.JsonProcessingException))) throw new ModelFailure(ModelFailure.Kind.NETWORK);
            throw new ModelFailure(ModelFailure.Kind.INVALID_RESPONSE);
        } finally {timer.cancel(false);}
    }
    private static class LimitedInput extends FilterInputStream {
        private int remaining=200000;
        LimitedInput(InputStream input) {super(input);}
        public int read() throws IOException {int value=super.read();if(value>=0 && --remaining<0) throw new ModelFailure(ModelFailure.Kind.INVALID_RESPONSE);return value;}
        public int read(byte[] bytes,int off,int len) throws IOException {
            int size=in.read(bytes,off,Math.min(len,Math.max(1,remaining+1)));
            if(size>0 && (remaining-=size)<0) throw new ModelFailure(ModelFailure.Kind.INVALID_RESPONSE);return size;
        }
    }
    public Map<String,Object> payload(List<Map<String,Object>> messages,Map<String,Object> tool) {
        return Map.of("model",model,"messages",messages,"stream",true,"thinking",Map.of("type","disabled"),
            "stream_options",Map.of("include_usage",true),"max_tokens",ModelControls.MAX_OUTPUT_TOKENS,
            "tools",List.of(Map.of("type","function","function",tool),Map.of("type","function","function",actionTool())));
    }
    private Map<String,Object> actionTool() {
        return Map.of("name","suggest_action","description","只在用户明确要求发布/接单/取消时提出操作建议，绝不执行。任务ID必须出现在用户原话中，否则填null。",
            "parameters",Map.of("type","object","properties",Map.of("action",Map.of("type","string","enum",List.of("PUBLISH","ACCEPT","CANCEL")),
                "demandId",Map.of("type",List.of("integer","null"))),"required",List.of("action","demandId"),"additionalProperties",false));
    }

}
