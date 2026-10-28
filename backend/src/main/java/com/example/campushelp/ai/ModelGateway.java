package com.example.campushelp.ai;

import com.example.campushelp.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;

/** Single provider; fail-fast capacity, persistent conservative dispatch budgets and no automatic retry. */
@Service
public class ModelGateway {
    public static final String PROMPT_VERSION="campus-needs-v3-user";
    private final DeepSeekClient client;private final ModelObservations observations;
    private final ModelCircuit circuit;private final ModelBudgets budgets;private final PromptContext context;
    private final Semaphore slots;
    private final Set<Long> running=ConcurrentHashMap.newKeySet();
    public ModelGateway(DeepSeekClient client,ModelObservations observations,ModelCircuit circuit,ModelBudgets budgets,PromptContext context,
        @Value("${model-gateway.max-concurrent:4}") int capacity) {
        if(capacity<1 || capacity>4) throw new IllegalArgumentException("Model concurrency must be between 1 and 4");
        this.client=client;this.observations=observations;this.circuit=circuit;this.budgets=budgets;this.context=context;this.slots=new Semaphore(capacity);
    }
    public Lease acquire(Long owner) {
        if(!client.configured()) {observations.reject();throw new BusinessException(503,"模型密钥未配置，可以继续使用完整表单");}
        if(!running.add(owner)) {observations.reject();throw new BusinessException(429,"请等待当前回复完成");}
        if(!slots.tryAcquire()) {running.remove(owner);observations.reject();throw new BusinessException(429,"模型服务繁忙，请稍后再试");}
        try {return new Lease(owner,circuit.acquire());}
        catch(RuntimeException e) {running.remove(owner);slots.release();observations.reject();throw e;}
    }
    public final class Lease implements AutoCloseable {
        private final Long owner;private final ModelCircuit.Permit permit;
        private final AtomicBoolean closed=new AtomicBoolean(),started=new AtomicBoolean();
        private Lease(Long owner,ModelCircuit.Permit permit) {this.owner=owner;this.permit=permit;}
        public void close() {if(closed.compareAndSet(false,true)) {circuit.abandon(permit);running.remove(owner);slots.release();}}
    }
    public DeepSeekClient.Reply stream(Lease lease,List<Map<String,Object>> messages,Map<String,Object> tool,Consumer<String> delta,DeepSeekClient.Cancellation cancellation) throws Exception {
        if(lease.closed.get() || !lease.started.compareAndSet(false,true)) throw new IllegalStateException("Model lease is no longer usable");
        String invocation=null;long started=System.nanoTime();var first=new AtomicReference<Long>();
        try {
            if(cancellation.cancelled()) throw cancellation.failure();
            var prepared=context.prepare(messages,tool);
            invocation=budgets.reserve(lease.owner,client.model());
            var reply=client.stream(prepared,tool,part->{if(!part.isEmpty()) first.compareAndSet(null,(System.nanoTime()-started)/1_000_000);delta.accept(part);},cancellation);
            budgets.finish(invocation,"SUCCEEDED",null,reply.usage());circuit.success(lease.permit);
            observations.record(client.model(),"SUCCEEDED",null,started,first.get(),reply.usage());return reply;
        } catch(Exception e) {
            String kind=e instanceof ModelFailure failure?failure.kind().name():e instanceof BusinessException?"POLICY_REJECTED":"INTERNAL";
            boolean cancelled=e instanceof ModelFailure failure && failure.kind()==ModelFailure.Kind.CLIENT_CANCELLED;
            String state=cancelled?"CANCELLED":"FAILED";
            circuit.failure(lease.permit,e instanceof ModelFailure failure && failure.tripsCircuit());
            if(invocation!=null) {
                try {budgets.finish(invocation,state,kind,null);}catch(RuntimeException ledgerUnavailable) {kind="LEDGER_UNCONFIRMED";}
            }
            if(invocation==null) observations.reject();
            else observations.record(client.model(),state,kind,started,first.get(),null);
            throw e;
        }
    }
}
