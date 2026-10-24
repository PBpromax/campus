package com.example.campushelp.transition;

import com.example.campushelp.exception.BusinessException;
import com.example.campushelp.observability.RequestTrace;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static com.example.campushelp.transition.TaskPreviewContracts.*;

/** Trusted fixed binary, minimal environment and bounded IPC. No shell, DB credentials or model text. */
@Component @Slf4j
public class CangjieTaskPreview {
    private final String binary,runtime;
    private final long timeoutMs;
    private final Semaphore slots=new Semaphore(2);
    private final ExecutorService readers=Executors.newVirtualThreadPerTaskExecutor();
    public CangjieTaskPreview(@Value("${task-preview.binary:}") String binary,@Value("${task-preview.runtime:}") String runtime,
        @Value("${task-preview.timeout-ms:3000}") long timeoutMs) {
        if(timeoutMs<100 || timeoutMs>5000) throw new IllegalArgumentException("Preview deadline must be 100..5000 ms");
        this.binary=binary;this.runtime=runtime;this.timeoutMs=timeoutMs;
    }
    @PreDestroy void close() {readers.shutdownNow();}
    public boolean configured() {return !binary.isBlank() && Path.of(binary).isAbsolute() && Files.isRegularFile(Path.of(binary)) && Files.isExecutable(Path.of(binary));}
    public Decision project(Snapshot task,Event event,Participant participant) {
        if(!configured()) throw new BusinessException(503,"仓颉状态预览尚未启用；可以继续使用原有确认流程");
        if(!Set.of("RUNNING","STUDY","BORROW","OTHER").contains(task.category()) || !statuses().contains(task.status()) ||
            task.id()<1 || task.id()>9007199254740991L || task.version()<0 || task.version()>9007199254740991L)
            throw new BusinessException(422,"当前任务不能转换为双语言模型");
        if(!slots.tryAcquire()) throw new BusinessException(429,"状态预览繁忙；可以稍后重试或继续核对原有确认卡");
        Process process=null;long started=System.nanoTime();String outcome="FAILED";
        try {
            var builder=new ProcessBuilder(binary).redirectErrorStream(true);
            builder.environment().clear();builder.environment().put("PATH","/usr/bin:/bin");
            if(!runtime.isBlank()) {builder.environment().put("DYLD_FALLBACK_LIBRARY_PATH",runtime);builder.environment().put("LD_LIBRARY_PATH",runtime);}
            process=builder.start();final Process child=process;
            var output=readers.submit(()->{
                try(var stream=child.getInputStream()) {
                    byte[] bytes=stream.readNBytes(4097);
                    if(bytes.length>4096) throw new java.io.IOException("Preview output limit");
                    return new String(bytes,StandardCharsets.UTF_8);
                }
            });
            String eventId="preview";
            String input="CAMPUS_TRANSITION_V1\tPARALLEL\nTASK\t"+task.id()+"\t"+task.category()+"\t"+task.status()+"\t"+task.version()+
                "\nEVENT\t"+task.id()+"\t"+eventId+"\t"+task.version()+"\t"+event+"\t"+participant+"\nEND\n";
            try(var stdin=process.getOutputStream()) {stdin.write(input.getBytes(StandardCharsets.UTF_8));}
            if(!process.waitFor(timeoutMs,TimeUnit.MILLISECONDS)) throw new TimeoutException();
            if(process.exitValue()!=0) throw new IllegalStateException("Preview process failed");
            String body=output.get(100,TimeUnit.MILLISECONDS);String[] rows=body.strip().split("\n");
            if(rows.length!=3 || !rows[0].equals("CAMPUS_TRANSITION_V1") || !rows[2].equals("SUMMARY\t1\t1")) throw new IllegalStateException("Invalid preview frame");
            String[] fields=rows[1].split("\t",-1);
            if(fields.length!=7 || !fields[0].equals("RESULT") || !fields[1].equals("0") || !fields[2].equals(Long.toString(task.id())) || !fields[3].equals(eventId) ||
                !Set.of("APPLIED","STATE_OR_ROLE_REJECTED","VERSION_CONFLICT","VERSION_EXHAUSTED").contains(fields[4]) || !statuses().contains(fields[5])) throw new IllegalStateException("Invalid preview result");
            long version=Long.parseLong(fields[6]);
            if(version<0 || version>9007199254740991L || (fields[4].equals("APPLIED")?version!=task.version()+1:version!=task.version())) throw new IllegalStateException("Invalid preview version");
            outcome=fields[4];return new Decision(outcome,fields[5],version);
        } catch(InterruptedException interrupted) {
            Thread.currentThread().interrupt();throw new BusinessException(503,"状态预览已停止；原任务没有被修改");
        } catch(Exception failure) {
            throw new BusinessException(503,"状态预览暂不可用；原任务没有被修改，可以继续使用确认流程");
        } finally {
            if(process!=null) process.destroyForcibly();slots.release();
            log.info("task_transition_preview traceId={} requestId={} outcome={} durationMs={}",RequestTrace.traceId(),RequestTrace.requestId(),outcome,(System.nanoTime()-started)/1_000_000);
        }
    }
    private Set<String> statuses() {return Set.of("PENDING","IN_PROGRESS","PENDING_CONFIRMATION","COMPLETED","CANCELLED");}
}
