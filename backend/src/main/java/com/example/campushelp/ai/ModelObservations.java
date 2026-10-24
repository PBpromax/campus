package com.example.campushelp.ai;

import com.example.campushelp.observability.RequestTrace;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.*;

/** Bounded process-local operational view; structured logs must be retained externally for longer windows. */
@Component @RequiredArgsConstructor @Slf4j
public class ModelObservations {
    public record Call(Instant at,String traceId,String requestId,String provider,String model,String promptVersion,
        String outcome,String failureKind,String contextVersion,long durationMs,Long firstTextMs,DeepSeekClient.Usage usage) {}
    public record Snapshot(String window,int retained,long succeeded,long failed,long cancelled,long rejected,
        long knownPromptTokens,long knownCompletionTokens,long usageUnknown,List<Call> recent) {}
    private final ObjectMapper json;
    private final Deque<Call> recent=new ArrayDeque<>();
    private long succeeded,failed,cancelled,rejected,promptTokens,completionTokens,usageUnknown;
    public synchronized void reject() { rejected++; }
    public void record(String model,String outcome,long started,Long firstTextMs,DeepSeekClient.Usage usage) {record(model,outcome,null,started,firstTextMs,usage);}
    public synchronized void record(String model,String outcome,String failureKind,long started,Long firstTextMs,DeepSeekClient.Usage usage) {
        var event=new Call(Instant.now(),RequestTrace.traceId(),RequestTrace.requestId(),"DeepSeek",model,ModelGateway.PROMPT_VERSION,
            outcome,failureKind,PromptContext.VERSION,(System.nanoTime()-started)/1_000_000,firstTextMs,usage);
        switch(outcome) { case "SUCCEEDED" -> succeeded++; case "CANCELLED" -> cancelled++; default -> failed++; }
        if(usage==null) usageUnknown++;else {promptTokens+=usage.promptTokens();completionTokens+=usage.completionTokens();}
        recent.addFirst(event);while(recent.size()>200) recent.removeLast();
        try {log.info("campus_observation {}",json.writeValueAsString(event));}
        catch(Exception ignored) {log.warn("Model observation serialization failed");}
    }
    public synchronized Snapshot snapshot() {
        return new Snapshot("PROCESS_LIFETIME_RESETS_ON_RESTART",recent.size(),succeeded,failed,cancelled,rejected,promptTokens,completionTokens,usageUnknown,List.copyOf(recent));
    }
}
