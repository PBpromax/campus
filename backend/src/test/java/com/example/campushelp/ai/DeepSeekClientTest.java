package com.example.campushelp.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DeepSeekClientTest {
    HttpServer server;String body;int status=200;String requestBody;
    ObjectMapper json=new ObjectMapper();
    @BeforeEach void start() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/chat/completions",exchange->{
            requestBody=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type","text/event-stream");exchange.sendResponseHeaders(status,0);
            exchange.getResponseBody().write(body.getBytes(StandardCharsets.UTF_8));exchange.close();
        });server.start();
    }
    @AfterEach void stop() { server.stop(0); }
    DeepSeekClient client() {return new DeepSeekClient(json,"fake-private-key","deepseek-flash","http://127.0.0.1:"+server.getAddress().getPort());}
    String chunk(Object delta,String finish) throws Exception {return "data: "+json.writeValueAsString(Map.of("choices",List.of(Map.of("delta",delta,"finish_reason",finish))))+"\n\n";}
    @Test void decodesTextAndFragmentedToolArguments() throws Exception {
        body=chunk(Map.of("content","你好"),"")+chunk(Map.of("tool_calls",List.of(Map.of("index",0,"function",Map.of("name","propose_draft","arguments","{\"fields\":")))),"")
          +chunk(Map.of("tool_calls",List.of(Map.of("index",0,"function",Map.of("arguments","{},\"candidates\":[]}")))),"tool_calls")+"data: [DONE]\n\n";
        var deltas=new ArrayList<String>();var r=client().stream(List.of(Map.of("role","user","content","fixture")),Map.of("name","propose_draft"),deltas::add,new DeepSeekClient.Cancellation());
        assertEquals("你好",r.text());assertEquals(List.of("你好"),deltas);assertEquals(0,json.readTree(r.arguments()).get("fields").size());
        assertFalse(requestBody.contains("fake-private-key"));assertEquals("disabled",json.readTree(requestBody).at("/thinking/type").asText());
    }
    @Test void truncatedStreamNeverProducesCompleteReply() throws Exception {
        body=chunk(Map.of("content","一部分"),"");
        assertThrows(Exception.class,()->client().stream(List.of(),Map.of(),x->{},new DeepSeekClient.Cancellation()));
    }
    @Test void tokenLimitIsNotSuccess() throws Exception {
        body=chunk(Map.of("content","一部分"),"length")+"data: [DONE]\n\n";
        assertThrows(Exception.class,()->client().stream(List.of(),Map.of(),x->{},new DeepSeekClient.Cancellation()));
    }
    @Test void upstreamErrorsNeverExposeProviderBodyOrKey() {
        body="fake-private-key";status=401;
        var e=assertThrows(Exception.class,()->client().stream(List.of(),Map.of(),x->{},new DeepSeekClient.Cancellation()));
        assertFalse(e.getMessage().contains("fake-private-key"));assertEquals(ModelFailure.Kind.UPSTREAM_AUTH,((ModelFailure)e).kind());
    }
    @Test void missingKeyMakesNoRequest() {
        var c=new DeepSeekClient(json,"","unused","http://127.0.0.1:"+server.getAddress().getPort());
        assertThrows(Exception.class,()->c.stream(List.of(),Map.of(),x->{},new DeepSeekClient.Cancellation()));assertNull(requestBody);
    }
    @Test void rejectsUnknownToolAndCancelledRequest() throws Exception {
        body=chunk(Map.of("tool_calls",List.of(Map.of("index",0,"function",Map.of("name","publish","arguments","{}")))),"tool_calls")+"data: [DONE]\n\n";
        assertThrows(Exception.class,()->client().stream(List.of(),Map.of(),x->{},new DeepSeekClient.Cancellation()));
        var cancelled=new DeepSeekClient.Cancellation();cancelled.cancel();
        assertThrows(Exception.class,()->client().stream(List.of(),Map.of(),x->{},cancelled));
    }
    @Test void readsUsageFromFinalChunkAndRequestsUsageExplicitly() throws Exception {
        body=chunk(Map.of("content","ok"),"")+"data: "+json.writeValueAsString(Map.of("choices",List.of(Map.of("delta",Map.of(),"finish_reason","stop")),
            "usage",Map.of("prompt_tokens",20,"completion_tokens",3,"total_tokens",23,"prompt_cache_hit_tokens",15,"prompt_cache_miss_tokens",5)))+"\n\ndata: [DONE]\n\n";
        var reply=client().stream(List.of(),Map.of(),x->{},new DeepSeekClient.Cancellation());
        assertEquals(new DeepSeekClient.Usage(20,3,23,15L,5L),reply.usage());
        assertTrue(json.readTree(requestBody).at("/stream_options/include_usage").asBoolean());
    }
    @Test void missingOrInvalidUsageIsUnknownRatherThanFree() throws Exception {
        for(var usage:List.of(Map.of(),Map.of("prompt_tokens",10,"completion_tokens",2,"total_tokens",1),Map.of("prompt_tokens",-1,"completion_tokens",2,"total_tokens",1))) {
            body="data: "+json.writeValueAsString(Map.of("choices",List.of(Map.of("delta",Map.of("content","ok"),"finish_reason","stop")),"usage",usage))+"\n\ndata: [DONE]\n\n";
            assertNull(client().stream(List.of(),Map.of(),x->{},new DeepSeekClient.Cancellation()).usage());
        }
    }

}
