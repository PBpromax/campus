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
        assertFalse(e.getMessage().contains("fake-private-key"));assertTrue(e.getMessage().contains("401"));
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
}
