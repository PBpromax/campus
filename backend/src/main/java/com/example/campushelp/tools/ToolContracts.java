package com.example.campushelp.tools;

import com.example.campushelp.dto.response.DemandResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class ToolContracts {
    public enum Action { PUBLISH, ACCEPT, CANCEL }
    public record Publication(@NotBlank @Size(max=100) String title,
        @NotNull @DecimalMin(value="0",inclusive=false) @DecimalMax("99999999.99") @Digits(integer=8,fraction=2) BigDecimal reward,
        @NotBlank @Size(max=200) String location,@NotNull @Future LocalDateTime expectedTime) { }
    public record Prepare(@NotNull Action action,@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{8,64}") String requestKey,
        @Positive Long sessionId,@PositiveOrZero Long revision,@Positive Long demandId,
        @Size(max=500) String reason,@Valid Publication publication) { }
    public record TaskSummary(long id,String title,String category,String status,BigDecimal reward,String location,Long publisherId,Long acceptorId) { }
    public record Confirm(@NotNull Boolean confirmed) { }
    public record Preview(String action,String title,String description,String category,String location,
        BigDecimal reward,LocalDateTime expectedTime,Long demandId,String beforeStatus,List<String> consequences) { }
    public record Receipt(String id,String action,String state,LocalDateTime expiresAt,Preview preview,
        DemandResponse result,Integer errorCode,String errorMessage,Long sourceSessionId,Long sourceRevision) { }
}
