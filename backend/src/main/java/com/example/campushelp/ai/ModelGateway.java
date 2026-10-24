package com.example.campushelp.ai;

import com.example.campushelp.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;

/** Single provider route. Capacity is fail-fast; no hidden queue, automatic retry or business execution. */
@Service
public class ModelGateway {
    public static final String PROMPT_VERSION="campus-needs-v1";
    private final DeepSeekClient client;
    private final ModelObservations observations;
    private final Semaphore slots;
    private final Set<Long> running=ConcurrentHashMap.newKeySet();
    public ModelGateway(DeepSeekClient client,ModelObservations observations,@Value("${model-gateway.max-concurrent:4}") int capacity) {
        if(capacity<1 || capacity>4) throw new IllegalArgumentException("Model concurrency must be between 1 and 4");
        this.client=client;this.observations=observations;this.slots=new Semaphore(capacity);
    }
    public Lease acquire(Long owner) {
        if(!client.configured()) {observations.reject();throw new BusinessException(503,"模型密钥未配置，可以继续使用完整表单");}
        if(!running.add(owner)) {observations.reject();throw new BusinessException(429,"请等待当前回复完成");}
        if(!slots.tryAcquire()) {running.remove(owner);observations.reject();throw new BusinessException(429,"模型服务繁忙，请稍后再试");}
        return new Lease(owner);
    }
    public final class Lease implements AutoCloseable {
        private final Long owner;private final AtomicBoolean closed=new AtomicBoolean();
        private Lease(Long owner) {this.owner=owner;}
        public void close() {if(closed.compareAndSet(false,true)) {running.remove(owner);slots.release();}}
    }
    public DeepSeekClient.Reply stream(List<Map<String,Object>> messages,Map<String,Object> tool,Consumer<String> delta,DeepSeekClient.Cancellation cancellation) throws Exception {
        long started=System.nanoTime();var first=new AtomicReference<Long>();
        try {
            var reply=client.stream(messages,tool,part->{if(!part.isEmpty()) first.compareAndSet(null,(System.nanoTime()-started)/1_000_000);delta.accept(part);},cancellation);
            observations.record(client.model(),"SUCCEEDED",started,first.get(),reply.usage());return reply;
        } catch(Exception e) {
            observations.record(client.model(),cancellation.cancelled()?"CANCELLED":"FAILED",started,first.get(),null);throw e;
        }
    }
}
