package com.example.campushelp.tools;
import com.example.campushelp.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import static com.example.campushelp.tools.ToolContracts.*;

@Service @RequiredArgsConstructor
public class ToolService {
    private final ToolTransactions transactions;
    private final ToolFailureRecorder failures;
    private final jakarta.validation.Validator validator;
    public Receipt prepare(Long actor,Prepare request) {
        try{
            if(!validator.validate(request).isEmpty()) throw new BusinessException(422,"请检查标题、正数报酬、位置和未来完成时间等执行参数");
            return transactions.prepare(actor,request);
        }
        catch(BusinessException e){failures.record(actor,null,request.action()==null?"UNKNOWN":request.action().name(),e.getCode(),e.getMessage(),false);throw e;}
    }
    public Receipt execute(Long actor,String id,Confirm request) {
        if(!Boolean.TRUE.equals(request.confirmed())) {
            failures.record(actor,id,"EXECUTE",422,"必须由用户明确确认执行",false);
            throw new BusinessException(422,"必须由用户明确确认执行");
        }
        try{return transactions.execute(actor,id);}
        catch(BusinessException e){failures.record(actor,id,"EXECUTE",e.getCode(),e.getMessage(),true);throw e;}
        catch(RuntimeException e){failures.record(actor,id,"EXECUTE",503,"执行结果暂不可确认，请先查询回执，不要重复准备新请求",true);throw new BusinessException(503,"执行结果暂不可确认，请先查询回执，不要重复准备新请求");}
    }
    public Receipt revoke(Long actor,String id) {
        try{return transactions.revoke(actor,id);}
        catch(BusinessException e){failures.record(actor,id,"REVOKE",e.getCode(),e.getMessage(),false);throw e;}
    }
}
