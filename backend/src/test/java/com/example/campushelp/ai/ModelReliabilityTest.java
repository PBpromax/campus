package com.example.campushelp.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class ModelReliabilityTest {
    HttpServer server;ExecutorService handlers;DeepSeekClient client;String mode="HEADERS";AtomicInteger requests=new AtomicInteger();CountDownLatch reached=new CountDownLatch(1);
    CountDownLatch releaseOversized=new CountDownLatch(1);
    @BeforeEach void start() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);handlers=Executors.newVirtualThreadPerTaskExecutor();server.setExecutor(handlers);
        server.createContext("/chat/completions",exchange->{
            requests.incrementAndGet();exchange.getRequestBody().readAllBytes();
            try {
                if(mode.equals("HEADERS")) {reached.countDown();Thread.sleep(2000);}
                exchange.getResponseHeaders().add("Content-Type","text/event-stream");exchange.sendResponseHeaders(200,0);
                String partial="data: {\"choices\":[{\"delta\":{\"content\":\"partial\"}}]}\n\n";
                if(mode.equals("BODY")) {exchange.getResponseBody().flush();reached.countDown();Thread.sleep(2000);}
                if(mode.equals("SLOW")) {for(int i=0;i<30;i++) {exchange.getResponseBody().write(": heartbeat\n\n".getBytes());exchange.getResponseBody().flush();Thread.sleep(60);}}
                if(mode.equals("PARTIAL")) {exchange.getResponseBody().write(partial.getBytes());exchange.getResponseBody().flush();reached.countDown();Thread.sleep(2000);}
                if(mode.equals("HUGE")) {
                    exchange.getResponseBody().write("x".repeat(250000).getBytes());
                    exchange.getResponseBody().flush();reached.countDown();
                    // Keep both newline and EOF absent so only the byte bound can reject the line.
                    releaseOversized.await(10,TimeUnit.SECONDS);
                }
            }catch(Exception ignored) {}finally {exchange.close();}
        });server.start();
        client=new DeepSeekClient(new ObjectMapper(),"fixture-private-key","deepseek-flash","http://127.0.0.1:"+server.getAddress().getPort(),300);
    }
    @AfterEach void close() {releaseOversized.countDown();client.close();server.stop(0);handlers.shutdownNow();}
    void timeout(String mode) {
        this.mode=mode;long start=System.nanoTime();
        var failure=assertThrows(ModelFailure.class,()->client.stream(List.of(),Map.of(),x->{},new DeepSeekClient.Cancellation()));
        assertEquals(ModelFailure.Kind.TIMEOUT,failure.kind());assertTrue((System.nanoTime()-start)/1_000_000<1500);assertEquals(1,requests.get());
    }
    @Test void missingHeadersHaveARealDeadline() {timeout("HEADERS");}
    @Test void headersDoNotDisableTheBodyDeadline() {timeout("BODY");}
    @Test void heartbeatsDoNotExtendTheWholeRequestDeadline() {timeout("SLOW");}
    @Test void partialTextThenStallNeverProducesCompleteReply() {timeout("PARTIAL");}
    @Test void cancellationAbortsAStalledBodyAndDoesNotBecomeTimeout() throws Exception {
        mode="PARTIAL";var cancellation=new DeepSeekClient.Cancellation();
        try(var runner=Executors.newVirtualThreadPerTaskExecutor()) {
            var future=runner.submit(()->assertThrows(ModelFailure.class,()->client.stream(List.of(),Map.of(),x->{},cancellation)));
            assertTrue(reached.await(1,TimeUnit.SECONDS));cancellation.cancel();assertEquals(ModelFailure.Kind.CLIENT_CANCELLED,future.get(1,TimeUnit.SECONDS).kind());
        }
    }
    @Test @Timeout(8) void oversizedLineIsRejectedWithoutWaitingForNewlineOrDeadline() throws Exception {
        // Separate byte-limit validation from the 300ms timeout cases above, including cold HTTP startup.
        client.close();
        client=new DeepSeekClient(new ObjectMapper(),"fixture-private-key","deepseek-flash","http://127.0.0.1:"+server.getAddress().getPort(),5000);
        mode="HUGE";var failure=assertThrows(ModelFailure.class,()->client.stream(List.of(),Map.of(),x->{},new DeepSeekClient.Cancellation()));
        assertEquals(ModelFailure.Kind.INVALID_RESPONSE,failure.kind());
        assertEquals(1,releaseOversized.getCount(),"The byte bound must fire before the server ends its body");
    }
    @Test void halfOpenAllowsExactlyOneProbeAndIgnoresStaleSuccess() {
        var config=new ModelControls();config.setFailureThreshold(2);config.setCircuitOpenMs(100);var clock=new AtomicLong(1000);var circuit=new ModelCircuit(config,clock::get);
        var stale=circuit.acquire();var first=circuit.acquire();circuit.failure(first,true);circuit.failure(circuit.acquire(),true);
        circuit.success(stale);assertEquals("OPEN",circuit.view().state());assertThrows(RuntimeException.class,circuit::acquire);
        clock.addAndGet(101);var probe=circuit.acquire();assertTrue(probe.probe());assertThrows(RuntimeException.class,circuit::acquire);
        circuit.failure(probe,true);clock.addAndGet(101);var recovery=circuit.acquire();circuit.success(recovery);assertEquals("CLOSED",circuit.view().state());
    }
    @Test void userCancellationAndUnusedProbeDoNotPoisonTheBreaker() {
        var config=new ModelControls();config.setFailureThreshold(1);config.setCircuitOpenMs(100);var clock=new AtomicLong(1000);var circuit=new ModelCircuit(config,clock::get);
        circuit.failure(circuit.acquire(),false);assertEquals("CLOSED",circuit.view().state());
        circuit.failure(circuit.acquire(),true);clock.addAndGet(101);var probe=circuit.acquire();circuit.abandon(probe);assertTrue(circuit.acquire().probe());
    }
}
