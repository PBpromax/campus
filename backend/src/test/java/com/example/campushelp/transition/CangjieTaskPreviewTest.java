package com.example.campushelp.transition;

import com.example.campushelp.exception.BusinessException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.example.campushelp.transition.TaskPreviewContracts.*;

class CangjieTaskPreviewTest {
    @TempDir Path temp;
    Path fixture(String script) throws Exception {
        Path executable=temp.resolve("preview-fixture");Files.writeString(executable,"#!/bin/sh\n"+script);assertTrue(executable.toFile().setExecutable(true));return executable;
    }
    Snapshot task() {return new Snapshot(1,"STUDY","PENDING",0);}
    @Test void disabledComponentDoesNotExecuteAnything() {
        var runner=new CangjieTaskPreview("","",3000);
        try {assertFalse(runner.configured());assertEquals(503,assertThrows(BusinessException.class,()->runner.project(task(),Event.ACCEPT,Participant.OTHER)).getCode());}
        finally {runner.close();}
    }
    @Test void fixedProtocolParsesOnlyBoundedTypedResults() throws Exception {
        Path executable=fixture("cat >/dev/null\nprintf 'CAMPUS_TRANSITION_V1\\nRESULT\\t0\\t1\\tpreview\\tAPPLIED\\tIN_PROGRESS\\t1\\nSUMMARY\\t1\\t1\\n'\n");
        var runner=new CangjieTaskPreview(executable.toString(),"",3000);
        try {assertEquals(new Decision("APPLIED","IN_PROGRESS",1),runner.project(task(),Event.ACCEPT,Participant.OTHER));}
        finally {runner.close();}
    }
    @Test void invalidResponseNeverBecomesAPreview() throws Exception {
        Path executable=fixture("cat >/dev/null\nprintf 'private fixture error'\n");
        var runner=new CangjieTaskPreview(executable.toString(),"",3000);
        try {var error=assertThrows(BusinessException.class,()->runner.project(task(),Event.ACCEPT,Participant.OTHER));assertEquals(503,error.getCode());assertFalse(error.getMessage().contains("private"));}
        finally {runner.close();}
    }
    @Test void hungChildIsKilledAndItsAdmissionSlotIsReleased() throws Exception {
        Path executable=fixture("cat >/dev/null\nexec sleep 10\n");
        var runner=new CangjieTaskPreview(executable.toString(),"",100);
        try {
            long start=System.nanoTime();
            for(int i=0;i<3;i++) assertEquals(503,assertThrows(BusinessException.class,()->runner.project(task(),Event.ACCEPT,Participant.OTHER)).getCode());
            assertTrue((System.nanoTime()-start)/1_000_000<2500);
        } finally {runner.close();}
    }
    @Test void oversizedOutputAndUnsupportedModelsAreRejected() throws Exception {
        Path executable=fixture("cat >/dev/null\nhead -c 5000 /dev/zero\n");
        var runner=new CangjieTaskPreview(executable.toString(),"",3000);
        try {
            assertEquals(503,assertThrows(BusinessException.class,()->runner.project(task(),Event.ACCEPT,Participant.OTHER)).getCode());
            assertEquals(422,assertThrows(BusinessException.class,()->runner.project(new Snapshot(1,"EXPRESS","PENDING",0),Event.ACCEPT,Participant.OTHER)).getCode());
        } finally {runner.close();}
    }
}
