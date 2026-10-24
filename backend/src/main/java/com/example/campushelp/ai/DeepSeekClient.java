package com.example.campushelp.ai;

import com.fasterxml.jackson.databind.*;
import org.springframework.beans.factory.annotation.Value;
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
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();
    public DeepSeekClient(ObjectMapper json, @Value("${deepseek.api-key:}") String key,
        @Value("${deepseek.model:deepseek-flash}") String model,
        @Value("${deepseek.base-url:https://api.deepseek.com}") String base) {
        this.json=json;this.key=key;this.model=model;this.base=base;
    }
    public boolean configured() { return !key.isBlank(); }
    public static class Cancellation {
        private volatile boolean cancelled;
        private InputStream input;
        public synchronized void attach(InputStream input) throws IOException {
            this.input=input; if(cancelled) input.close();
        }
        public synchronized void cancel() { cancelled=true;try { if(input!=null) input.close(); } catch(IOException ignored) {} }
        public boolean cancelled() { return cancelled; }
    }
    public record Reply(String text,String arguments) { }
    public Reply stream(List<Map<String,Object>> messages,Map<String,Object> tool, Consumer<String> delta, Cancellation cancellation) throws Exception {
        if(cancellation.cancelled()) throw new IOException("请求已中断");
        if(!configured()) throw new IOException("模型密钥未配置");
        var payload=Map.of("model",model,"messages",messages,"stream",true,"thinking",Map.of("type","disabled"),
            "max_tokens",1600,"tools",List.of(Map.of("type","function","function",tool)));
        var request=HttpRequest.newBuilder(URI.create(base+"/chat/completions")).timeout(Duration.ofSeconds(45))
            .header("Authorization","Bearer "+key).header("Content-Type","application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload))).build();
        var response=http.send(request,HttpResponse.BodyHandlers.ofInputStream());
        cancellation.attach(response.body());
        try(var reader=new BufferedReader(new InputStreamReader(response.body(),StandardCharsets.UTF_8))) {
            if(response.statusCode()!=200) throw new IOException("模型服务暂不可用（HTTP "+response.statusCode()+"）");
            StringBuilder text=new StringBuilder(),args=new StringBuilder(),name=new StringBuilder();
            int size=0;boolean done=false;String finish="";
            for(String line;(line=reader.readLine())!=null;) {
                if(cancellation.cancelled()) throw new IOException("请求已中断");
                size+=line.length();if(size>200000) throw new IOException("模型响应超限");
                if(!line.startsWith("data:")) continue;
                String data=line.substring(5).strip();
                if(data.equals("[DONE]")) { done=true;break; }
                if(data.isEmpty()) continue;
                JsonNode chunk=json.readTree(data);
                if(chunk.has("error")) throw new IOException("模型服务返回错误");
                for(JsonNode choice:chunk.path("choices")) {
                    if(!choice.path("finish_reason").isNull() && choice.has("finish_reason")) finish=choice.path("finish_reason").asText();
                    var d=choice.path("delta");
                    if(d.path("content").isTextual()) {
                        String part=d.get("content").asText();text.append(part);
                        if(text.length()>4000) throw new IOException("回复过长，请缩小问题范围");
                        if(!part.isEmpty()) delta.accept(part);
                    }
                    for(var call:d.path("tool_calls")) {
                        if(call.path("index").asInt(-1)!=0) throw new IOException("不支持多项模型操作");
                        var f=call.path("function");name.append(f.path("name").asText(""));args.append(f.path("arguments").asText(""));
                        if(args.length()>24000) throw new IOException("建议内容超限");
                    }
                }
            }
            if(!done || !Set.of("stop","tool_calls").contains(finish)) throw new IOException("模型回复未完整结束，请重新核对后重试");
            if(!name.isEmpty() && !name.toString().equals("propose_draft")) throw new IOException("拒绝未知模型操作");
            if(name.isEmpty() && !args.isEmpty()) throw new IOException("模型建议格式无效");
            if(text.isEmpty() && args.isEmpty()) throw new IOException("模型未返回有效内容");
            return new Reply(text.toString(),args.toString());
        }
    }
}
