package com.example.campushelp.transition;

import com.example.campushelp.exception.BusinessException;
import com.example.campushelp.repository.DemandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;
import static com.example.campushelp.transition.TaskPreviewContracts.*;

@Service @RequiredArgsConstructor
public class TaskPreviewService {
    private final DemandRepository demands;
    private final CangjieTaskPreview component;
    @Transactional(readOnly=true)
    public Preview preview(Long actor,Request request) {
        var task=demands.findById(request.demandId()).orElseThrow(()->new BusinessException(404,"任务不存在"));
        boolean publisher=Objects.equals(actor,task.getPublisherId()),acceptor=Objects.equals(actor,task.getAcceptorId());
        if(!task.getStatus().equals("PENDING") && !publisher && !acceptor) throw new BusinessException(404,"任务不存在或无权预览");
        Participant participant=publisher?Participant.PUBLISHER:acceptor?Participant.ACCEPTOR:Participant.OTHER;
        var source=new Snapshot(task.getId(),task.getCategory(),task.getStatus(),task.getRowVersion()==null?0:task.getRowVersion());
        var decision=component.project(source,request.event(),participant);
        return new Preview("Cangjie 1.2.0 / transition-v1",source,participant,request.event(),decision,
            "只预览状态变化，尚未执行；不代表已通过信用、取消时限或其他业务检查。真实操作仍须准备确认卡并明确执行，执行时重新核对。");
    }
}
