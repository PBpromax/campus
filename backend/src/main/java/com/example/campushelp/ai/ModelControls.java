package com.example.campushelp.ai;

import jakarta.validation.constraints.*;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import java.math.BigDecimal;

@Component @ConfigurationProperties(prefix="model-controls") @Validated @Data
public class ModelControls {
    @Min(1) @Max(20) private int failureThreshold=3;
    @Min(100) @Max(300000) private long circuitOpenMs=30000;
    @Min(1) private int userCallsPerDay=20;
    @Min(1) private int globalCallsPerDay=100;
    @Min(1) private long userTokensPerDay=2000000;
    @Min(1) private long globalTokensPerDay=10000000;
    @DecimalMin("0.00000001") private BigDecimal userUsdPerDay=new BigDecimal("0.50");
    @DecimalMin("0.00000001") private BigDecimal globalUsdPerDay=new BigDecimal("2.00");
    @DecimalMin("0") private BigDecimal inputHitUsdPerMillion=new BigDecimal("0.006");
    @DecimalMin("0.00000001") private BigDecimal inputMissUsdPerMillion=new BigDecimal("0.30");
    @DecimalMin("0.00000001") private BigDecimal outputUsdPerMillion=new BigDecimal("1.20");
    @NotBlank @Size(max=64) private String pricedModel="deepseek-flash";
    @NotBlank @Size(max=64) private String priceVersion="deepseek-flash-20261024-peak-usd";
    @AssertTrue public boolean isPriceOrderValid() {return inputHitUsdPerMillion.compareTo(inputMissUsdPerMillion)<=0;}
    public static final int MAX_PAYLOAD_BYTES=48000, RESERVED_INPUT_TOKENS=65536, MAX_OUTPUT_TOKENS=1600;
    public static final long RESERVED_TOKENS=RESERVED_INPUT_TOKENS+MAX_OUTPUT_TOKENS;
}
