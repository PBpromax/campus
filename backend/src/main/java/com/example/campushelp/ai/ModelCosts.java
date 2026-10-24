package com.example.campushelp.ai;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.math.*;

@Component @RequiredArgsConstructor
public class ModelCosts {
    public record Estimate(BigDecimal usd,String basis) {}
    private final ModelControls controls;
    public BigDecimal reservation() {return amount(ModelControls.RESERVED_INPUT_TOKENS,controls.getInputMissUsdPerMillion())
        .add(amount(ModelControls.MAX_OUTPUT_TOKENS,controls.getOutputUsdPerMillion()));}
    public Estimate estimate(DeepSeekClient.Usage usage) {
        if(usage==null) return new Estimate(null,"USAGE_UNKNOWN");
        BigDecimal input;
        boolean split=usage.cacheHitTokens()!=null && usage.cacheMissTokens()!=null;
        if(split) input=amount(usage.cacheHitTokens(),controls.getInputHitUsdPerMillion()).add(amount(usage.cacheMissTokens(),controls.getInputMissUsdPerMillion()));
        else input=amount(usage.promptTokens(),controls.getInputMissUsdPerMillion());
        return new Estimate(input.add(amount(usage.completionTokens(),controls.getOutputUsdPerMillion())),split?"PEAK_SNAPSHOT_ESTIMATE":"PEAK_MISS_UPPER_ESTIMATE");
    }
    private BigDecimal amount(long tokens,BigDecimal rate) {return BigDecimal.valueOf(tokens).multiply(rate).divide(new BigDecimal("1000000"),8,RoundingMode.CEILING);}
}
