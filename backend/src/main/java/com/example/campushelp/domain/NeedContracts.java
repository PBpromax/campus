package com.example.campushelp.domain;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.*;

public final class NeedContracts {
    private NeedContracts() { }
    public record Field(String key, String label, String type, List<String> options) { }
    public record Catalog(NeedCategory category, List<Field> fields) { }
    public record Version(@NotNull @PositiveOrZero Long revision) { }
    public record Select(@NotNull @PositiveOrZero Long revision, @NotNull NeedCategory category) { }
    public record Edit(@NotNull @PositiveOrZero Long revision,
                       @NotNull @Size(max=30) Map<@NotBlank String, @NotNull @Size(max=6000) String> fields,
                       @NotNull @Size(max=10) List<@NotBlank String> contexts) { }
    public record Message(@NotNull @PositiveOrZero Long revision, @NotBlank @Size(max=4000) String text) { }
    public record Assessment(boolean ready, List<String> missing, List<String> conflicts) { }
    public record Draft(long id, NeedCategory category, long revision, Long confirmedRevision,
                        Map<String,String> fields, Map<String,String> provenance,
                        List<String> contexts, Assessment assessment) { }
    public record Conversation(long id, NeedCategory category, String role, String text, LocalDateTime createdAt) { }
    public record Session(long id, long revision, NeedCategory activeCategory, boolean routePending,
                          String intakeText, List<NeedCategory> candidates, List<Draft> drafts,
                          List<Conversation> messages, LocalDateTime updatedAt) { }
}
