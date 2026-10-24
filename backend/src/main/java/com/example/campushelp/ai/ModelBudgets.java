package com.example.campushelp.ai;

import com.example.campushelp.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import java.time.*;
import java.math.BigDecimal;
import java.util.List;

@Service @RequiredArgsConstructor
public class ModelBudgets {
    public record View(LocalDate day,String timezone,String currency,String priceVersion,long calls,long reservedTokens,BigDecimal reservedUsd,
        int callLimit,long tokenLimit,BigDecimal estimateLimitUsd,List<ModelInvocation> recent) {}
    private final ModelBudgetTransactions transactions;
    private final ModelBudgetDayRepository days;
    private final ModelInvocationRepository invocations;
    private final ModelControls controls;
    LocalDate day() {return LocalDate.now(ZoneId.of("Asia/Shanghai"));}
    public String reserve(Long owner,String model) {
        var day=day();
        try {
            try {transactions.ensureDay(day);}catch(DataIntegrityViolationException race) {if(!days.existsById(ModelBudgetTransactions.globalId(day))) throw race;}
            return transactions.reserve(owner,day,model);
        }
        catch(BusinessException e) {throw e;}
        catch(RuntimeException unavailable) {throw new BusinessException(503,"模型额度账本暂不可用，未发起模型请求；可以继续使用完整表单");}
    }
    public void finish(String id,String state,String failure,DeepSeekClient.Usage usage) {transactions.finish(id,state,failure,usage);}
    public View own(Long owner) {
        var day=day();var row=days.findById(ModelBudgetTransactions.ownerId(day,owner)).orElseGet(ModelBudgetDay::new);
        return new View(day,"Asia/Shanghai","USD",controls.getPriceVersion(),row.getAttempts(),row.getReservedTokens(),row.getReservedUsd(),
            controls.getUserCallsPerDay(),controls.getUserTokensPerDay(),controls.getUserUsdPerDay(),invocations.findTop20ByOwnerIdAndBudgetDayOrderByCreatedAtDesc(owner,day));
    }
}
