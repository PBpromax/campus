package com.example.campushelp.ai;

import com.example.campushelp.exception.BusinessException;
import com.example.campushelp.observability.RequestTrace;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ModelGatewayTest {
    DeepSeekClient client=mock(DeepSeekClient.class);
    ModelBudgets budgets=mock(ModelBudgets.class);
    ModelControls controls=new ModelControls();
    ModelObservations observations=new ModelObservations(new ObjectMapper().findAndRegisterModules());
    @BeforeEach void setup() {when(client.configured()).thenReturn(true);when(client.model()).thenReturn("fixture-model");}
    ModelGateway gateway(int capacity) {return new ModelGateway(client,observations,new ModelCircuit(controls),budgets,new PromptContext(new ObjectMapper()),capacity);}
    DeepSeekClient.Reply call(ModelGateway gateway,java.util.List<java.util.Map<String,Object>> messages,DeepSeekClient.Cancellation cancellation) throws Exception {try(var lease=gateway.acquire(1L)) {return gateway.stream(lease,messages,Map.of(),x->{},cancellation);}}
    @Test void perUserAndGlobalAdmissionReleaseExactlyOnce() {
        var gateway=gateway(2);
        var one=gateway.acquire(1L);var two=gateway.acquire(2L);
        assertEquals(429,assertThrows(BusinessException.class,()->gateway.acquire(1L)).getCode());
        assertEquals(429,assertThrows(BusinessException.class,()->gateway.acquire(3L)).getCode());
        one.close();one.close();var three=gateway.acquire(3L);
        assertThrows(BusinessException.class,()->gateway.acquire(4L));
        two.close();three.close();try(var again=gateway.acquire(1L)) {assertNotNull(again);}
        assertEquals(3,observations.snapshot().rejected());
    }
    @Test void missingProviderConfigNeverOccupiesCapacity() {
        var gateway=gateway(1);when(client.configured()).thenReturn(false);
        assertEquals(503,assertThrows(BusinessException.class,()->gateway.acquire(1L)).getCode());
        when(client.configured()).thenReturn(true);try(var next=gateway.acquire(1L)) {assertNotNull(next);}
    }
    @Test void recordsKnownUsageFirstTextAndTraceWithoutMessages() throws Exception {
        var gateway=gateway(1);String id=UUID.randomUUID().toString();
        doAnswer(inv->{Consumer<String> emit=inv.getArgument(2);emit.accept("private-content");return new DeepSeekClient.Reply("private-content","","",new DeepSeekClient.Usage(10,2,12,8L,2L));})
            .when(client).stream(anyList(),anyMap(),any(),any());
        try(var scope=RequestTrace.use(Map.of("traceId",id,"requestId",id))) {
            call(gateway,List.of(Map.of("content","private-input")),new DeepSeekClient.Cancellation());
        }
        var view=observations.snapshot();assertEquals(1,view.succeeded());assertEquals(10,view.knownPromptTokens());assertEquals(2,view.knownCompletionTokens());
        assertEquals(0,view.usageUnknown());assertNotNull(view.recent().getFirst().firstTextMs());assertEquals(id,view.recent().getFirst().traceId());
        String output=new ObjectMapper().findAndRegisterModules().writeValueAsString(view);
        assertFalse(output.contains("private-content"));assertFalse(output.contains("private-input"));
    }
    @Test void failureAndCancellationHaveUnknownUsageAndNoAutomaticRetry() throws Exception {
        var gateway=gateway(1);
        when(budgets.reserve(anyLong(),anyString())).thenReturn("fixture-invocation");
        when(client.stream(anyList(),anyMap(),any(),any())).thenThrow(new ModelFailure(ModelFailure.Kind.NETWORK));
        var cancellation=new DeepSeekClient.Cancellation();
        assertThrows(IOException.class,()->call(gateway,List.of(),cancellation));
        cancellation.cancel();assertThrows(IOException.class,()->call(gateway,List.of(),cancellation));
        assertEquals(1,observations.snapshot().failed());assertEquals(0,observations.snapshot().cancelled());assertEquals(1,observations.snapshot().usageUnknown());
        verify(client,times(1)).stream(anyList(),anyMap(),any(),any());
        assertFalse(observations.snapshot().toString().contains("private upstream body"));
    }
    @Test void retentionIsBoundedButCountersCoverProcessLifetime() {
        for(int i=0;i<205;i++) observations.record("fixture","SUCCEEDED",System.nanoTime(),null,null);
        assertEquals(200,observations.snapshot().retained());assertEquals(205,observations.snapshot().succeeded());assertEquals(205,observations.snapshot().usageUnknown());
    }
}
