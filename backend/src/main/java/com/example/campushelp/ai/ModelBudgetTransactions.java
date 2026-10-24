package com.example.campushelp.ai;

import com.example.campushelp.exception.BusinessException;
import com.example.campushelp.observability.RequestTrace;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class ModelBudgetTransactions {
    private final ModelBudgetDayRepository days;
    private final ModelInvocationRepository invocations;
    private final ModelControls controls;
    private final ModelCosts costs;
    public static String globalId(LocalDate day) {return day+":GLOBAL";}
    public static String ownerId(LocalDate day,Long owner) {return day+":USER:"+owner;}
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void ensureDay(LocalDate day) {
        if(!days.existsById(globalId(day))) {var row=new ModelBudgetDay();row.setId(globalId(day));days.saveAndFlush(row);}
    }
    @Transactional
    public String reserve(Long owner,LocalDate day,String model) {
        if(!controls.getPricedModel().equals(model)) throw new BusinessException(503,"当前模型尚未配置对应价格快照，请使用完整表单");
        var global=days.lock(globalId(day)).orElseThrow();
        var user=days.findById(ownerId(day,owner)).orElseGet(()->{var row=new ModelBudgetDay();row.setId(ownerId(day,owner));return row;});
        var usd=costs.reservation();
        check(global,controls.getGlobalCallsPerDay(),controls.getGlobalTokensPerDay(),controls.getGlobalUsdPerDay(),usd);
        check(user,controls.getUserCallsPerDay(),controls.getUserTokensPerDay(),controls.getUserUsdPerDay(),usd);
        consume(global,usd);consume(user,usd);days.save(global);days.save(user);
        var attempt=new ModelInvocation();attempt.setId(UUID.randomUUID().toString());attempt.setOwnerId(owner);attempt.setBudgetDay(day);
        attempt.setTraceId(RequestTrace.traceId());attempt.setRequestId(RequestTrace.requestId());attempt.setModel(model);attempt.setPriceVersion(controls.getPriceVersion());
        attempt.setReservedTokens(ModelControls.RESERVED_TOKENS);attempt.setReservedUsd(usd);invocations.saveAndFlush(attempt);return attempt.getId();
    }
    private void check(ModelBudgetDay row,long maxCalls,long maxTokens,BigDecimal maxUsd,BigDecimal reserveUsd) {
        if(row.getAttempts()>=maxCalls || row.getReservedTokens()>maxTokens-ModelControls.RESERVED_TOKENS || row.getReservedUsd().add(reserveUsd).compareTo(maxUsd)>0)
            throw new BusinessException(429,"本平台今日模型额度已达上限（次数、Token或估算费用），可继续使用完整表单，次日再试");
    }
    private void consume(ModelBudgetDay row,BigDecimal usd) {row.setAttempts(row.getAttempts()+1);row.setReservedTokens(row.getReservedTokens()+ModelControls.RESERVED_TOKENS);row.setReservedUsd(row.getReservedUsd().add(usd));}
    @Transactional
    public void finish(String id,String state,String failure,DeepSeekClient.Usage usage) {
        var attempt=invocations.lock(id).orElseThrow();if(!attempt.getState().equals("RESERVED")) return;
        attempt.setState(state);attempt.setFailureKind(failure);attempt.setFinishedAt(Instant.now());
        var estimate=costs.estimate(usage);attempt.setEstimatedUsd(estimate.usd());attempt.setEstimateBasis(estimate.basis());
        if(usage!=null) {attempt.setPromptTokens(usage.promptTokens());attempt.setCompletionTokens(usage.completionTokens());}
        // No refunds: timeout, cancellation, crash and unknown usage cannot reset dispatch quota.
    }
}
