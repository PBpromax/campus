package com.example.campushelp.ai;

import com.example.campushelp.exception.BusinessException;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.function.LongSupplier;

/** Single-instance breaker; late successes from an older generation cannot close a newer open circuit. */
@Component
public class ModelCircuit {
    public record Permit(long generation,boolean probe) {}
    public record View(String state,int consecutiveFailures,long retryAfterMs) {}
    private final ModelControls controls;private final LongSupplier now;
    private long generation,openUntil;private int failures;private boolean probing;
    @Autowired public ModelCircuit(ModelControls controls) {this(controls,()->System.nanoTime()/1_000_000);}
    ModelCircuit(ModelControls controls,LongSupplier now) {this.controls=controls;this.now=now;}
    public synchronized Permit acquire() {
        if(openUntil!=0) {
            if(now.getAsLong()<openUntil || probing) throw new BusinessException(503,"模型正在故障冷却，请稍后再试；可继续使用完整表单");
            probing=true;return new Permit(generation,true);
        }
        return new Permit(generation,false);
    }
    public synchronized void success(Permit permit) {
        if(permit.generation()!=generation) return;
        if(openUntil==0 || permit.probe()) {failures=0;openUntil=0;probing=false;}
    }
    public synchronized void failure(Permit permit,boolean count) {
        if(permit.generation()!=generation) return;
        if(!count) {abandon(permit);return;}
        failures++;
        if(permit.probe() || failures>=controls.getFailureThreshold()) {generation++;openUntil=now.getAsLong()+controls.getCircuitOpenMs();probing=false;}
    }
    public synchronized void abandon(Permit permit) {if(permit.generation()==generation && permit.probe()) probing=false;}
    public synchronized View view() {return new View(openUntil==0?"CLOSED":probing?"HALF_OPEN":"OPEN",failures,Math.max(0,openUntil-now.getAsLong()));}
}
