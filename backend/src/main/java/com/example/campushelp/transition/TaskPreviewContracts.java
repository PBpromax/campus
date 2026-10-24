package com.example.campushelp.transition;

import jakarta.validation.constraints.*;

public final class TaskPreviewContracts {
    private TaskPreviewContracts() {}
    public enum Event { ACCEPT, SUBMIT, CONFIRM, CANCEL }
    public enum Participant { PUBLISHER, ACCEPTOR, OTHER }
    public record Request(@NotNull @Positive @Max(9007199254740991L) Long demandId,@NotNull Event event) {}
    public record Snapshot(long id,String category,String status,long version) {}
    public record Decision(String outcome,String status,long version) {}
    public record Preview(String engine,Snapshot source,Participant participant,Event event,Decision decision,String notice) {}
}
