package com.example.campushelp.tools;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service @RequiredArgsConstructor
public class ToolFailureRecorder {
    private final ToolIntentRepository intents;
    private final ToolTransactions transactions;
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void record(Long actor,String id,String action,int code,String message,boolean terminal) {
        if(id!=null) {
            var owned=intents.lockOwned(id,actor);
            if(owned.isPresent()) {
                var intent=owned.get();action=intent.getAction();
                if(terminal && intent.getState().equals("PREPARED")) {intent.setState("FAILED");intent.setErrorCode(code);intent.setErrorMessage(message);}
            }
        }
        transactions.audit(actor,id,action,terminal?"FAILED":"REJECTED",message);
    }
}
