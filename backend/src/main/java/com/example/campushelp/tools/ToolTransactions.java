package com.example.campushelp.tools;

import com.example.campushelp.domain.*;
import com.example.campushelp.entity.*;
import com.example.campushelp.exception.BusinessException;
import com.example.campushelp.repository.*;
import com.example.campushelp.service.DemandService;
import com.example.campushelp.dto.request.DemandRequest;
import com.example.campushelp.dto.response.DemandResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import static com.example.campushelp.tools.ToolContracts.*;

@Service @RequiredArgsConstructor
public class ToolTransactions {
    private final ToolIntentRepository intents;
    private final ToolAuditRepository audits;
    private final UserRepository users;
    private final DemandRepository demands;
    private final DemandService service;
    private final NeedSessionRepository sessions;
    private final NeedService needs;
    private final NeedRules rules;
    private final ObjectMapper json;

    @Transactional
    public Receipt prepare(Long actor,Prepare request) {
        var user=lockUser(actor);
        String hash=hash(write(request));
        var existing=intents.findByActorIdAndRequestKey(actor,request.requestKey());
        if(existing.isPresent()) {
            if(!existing.get().getRequestHash().equals(hash)) throw new BusinessException(409,"同一请求标识不能用于不同内容");
            return view(existing.get());
        }
        var intent=new ToolIntent();intent.setId(UUID.randomUUID().toString());intent.setActorId(actor);
        intent.setRequestKey(request.requestKey());intent.setRequestHash(hash);intent.setAction(request.action().name());
        intent.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        Preview preview;
        if(request.action()==Action.PUBLISH) {
            if(request.sessionId()==null || request.revision()==null || request.publication()==null || request.demandId()!=null)
                throw new BusinessException(422,"发布需要本人已确认草稿、版本及发布信息");
            if(request.reason()!=null && !request.reason().isBlank()) throw new BusinessException(422,"发布不能携带取消理由");
            var source=confirmed(actor,request.sessionId(),request.revision());
            var draft=source.drafts().stream().filter(d->d.category()==source.activeCategory()).findFirst().orElseThrow();
            checkPublishCredit(user);
            String publicationKey=source.id()+":"+draft.category()+":"+draft.revision();
            if(intents.existsByPublicationKey(publicationKey)) throw new BusinessException(409,"此版本草稿已经发布，请查看历史回执");
            if(draft.category()==NeedCategory.OTHER && "希望拆分为单人任务".equals(draft.fields().get("splitMode")))
                throw new BusinessException(422,"暂不支持一次拆分多任务，请改为一人协调或分别建立草稿");
            String description=description(draft);
            if(description.length()>500) throw new BusinessException(422,"完整约定超过原任务500字限制，请缩短草稿内容后重新确认；不会自动截断");
            var p=request.publication();
            preview=new Preview("PUBLISH",p.title(),description,draft.category().name(),p.location(),p.reward(),p.expectedTime(),null,"NEW",
                List.of("将创建真实任务，等待其他同学接单；AI不能代替他人同意。","报酬为约定金额，系统尚未接入真实支付。","发布者取消仅限未接单且在原系统30分钟窗口内；执行时重新检查。"));
            intent.setSourceSessionId(source.id());intent.setSourceRevision(source.revision());intent.setDraftRevision(draft.revision());
        } else {
            if(request.demandId()==null || request.sessionId()!=null || request.revision()!=null || request.publication()!=null)
                throw new BusinessException(422,"接单或取消只接受明确的任务编号及相应理由");
            var demand=demands.lockById(request.demandId()).orElseThrow(()->new BusinessException(404,"任务不存在"));
            eligibility(request.action(),user,demand,request.reason());
            intent.setDemandId(demand.getId());intent.setDemandVersion(demand.getRowVersion());
            var consequences=request.action()==Action.ACCEPT?List.of("将以当前账号真实接单，状态改为进行中。","支付状态HELD仅为系统字段，不表示真实扣款。"):
                actor.equals(demand.getPublisherId())?List.of("将取消本人尚未被接单的任务。","取消窗口在执行时重新检查；REFUNDED仅为状态字段。"):
                List.of("取消将终止当前任务，并扣除本人5分信用分。","低信用可能触发原系统限制；不会发生真实资金退款。");
            preview=new Preview(request.action().name(),demand.getTitle(),request.action()==Action.CANCEL?(request.reason()==null?"":request.reason().trim()):demand.getDescription(),
                demand.getCategory(),demand.getLocation(),demand.getReward(),demand.getExpectedTime(),demand.getId(),demand.getStatus(),consequences);
        }
        intent.setPayloadJson(write(request));intent.setPreviewJson(write(preview));intents.saveAndFlush(intent);
        audit(actor,intent.getId(),intent.getAction(),"PREPARED","已生成待用户核对的执行内容");return view(intent);
    }
    @Transactional
    public Receipt execute(Long actor,String id) {
        var user=lockUser(actor);
        var intent=intents.lockOwned(id,actor).orElseThrow(()->new BusinessException(404,"执行请求不存在"));
        if(intent.getState().equals("SUCCEEDED")) return view(intent);
        if(!intent.getState().equals("PREPARED")) throw new BusinessException(intent.getErrorCode()==null?409:intent.getErrorCode(),intent.getErrorMessage()==null?"请求已撤回或失效":intent.getErrorMessage());
        if(intent.getExpiresAt().isBefore(LocalDateTime.now())) throw new BusinessException(409,"确认内容已过期，请重新准备");
        var request=read(intent.getPayloadJson(),Prepare.class);var action=Action.valueOf(intent.getAction());
        DemandResponse result;
        if(action==Action.PUBLISH) {
            var source=confirmed(actor,intent.getSourceSessionId(),intent.getSourceRevision());
            var d=source.drafts().stream().filter(x->x.category()==source.activeCategory()).findFirst().orElseThrow();
            if(d.revision()!=intent.getDraftRevision()) throw new BusinessException(409,"草稿已变更，请重新准备");
            String key=source.id()+":"+d.category()+":"+d.revision();
            if(intents.existsByPublicationKey(key)) throw new BusinessException(409,"此版本草稿已经发布");
            checkPublishCredit(user);
            var preview=read(intent.getPreviewJson(),Preview.class);
            if(!preview.expectedTime().isAfter(LocalDateTime.now())) throw new BusinessException(422,"期望完成时间已过，请重新准备");
            var publish=new DemandRequest();publish.setTitle(preview.title());publish.setDescription(preview.description());publish.setCategory(preview.category());
            publish.setLocation(preview.location());publish.setReward(preview.reward());publish.setExpectedTime(preview.expectedTime());
            result=service.createDemand(actor,publish);intent.setPublicationKey(key);intent.setDemandId(result.getId());
        } else {
            var demand=demands.lockById(intent.getDemandId()).orElseThrow(()->new BusinessException(404,"任务不存在"));
            if(!Objects.equals(demand.getRowVersion(),intent.getDemandVersion())) throw new BusinessException(409,"任务已变更，请重新核对");
            eligibility(action,user,demand,request.reason());
            result=action==Action.ACCEPT?service.acceptDemand(demand.getId(),actor):service.cancelDemand(demand.getId(),actor,request.reason());
        }
        demands.flush();users.flush();
        result=service.getDemandById(result.getId());
        intent.setResultJson(write(result));intent.setState("SUCCEEDED");intents.saveAndFlush(intent);
        audit(actor,id,intent.getAction(),"SUCCEEDED","任务#"+result.getId()+"，结果状态："+result.getStatus());return view(intent);
    }
    @Transactional
    public Receipt revoke(Long actor,String id) {
        lockUser(actor);var intent=intents.lockOwned(id,actor).orElseThrow(()->new BusinessException(404,"执行请求不存在"));
        if(intent.getState().equals("REVOKED")) return view(intent);
        if(!intent.getState().equals("PREPARED")) throw new BusinessException(409,"只能撤回尚未执行的请求");
        intent.setState("REVOKED");audit(actor,id,intent.getAction(),"REVOKED","用户撤回，未执行业务操作");return view(intent);
    }
    @Transactional(readOnly=true) public List<Receipt> list(Long actor) {
        return intents.findByActorIdOrderByCreatedAtDesc(actor,PageRequest.of(0,20)).stream().map(this::view).toList();
    }
    @Transactional(readOnly=true) public List<TaskSummary> tasks(Long actor,String scope) {
        List<Demand> available;
        if(scope.equals("available")) available=demands.findByStatusOrderByCreatedAtDesc("PENDING").stream().filter(d->!actor.equals(d.getPublisherId())).toList();
        else if(scope.equals("mine")) {
            var unique=new LinkedHashMap<Long,Demand>();demands.findByPublisherId(actor).forEach(d->unique.put(d.getId(),d));demands.findByAcceptorId(actor).forEach(d->unique.put(d.getId(),d));
            available=unique.values().stream().sorted(Comparator.comparing(Demand::getCreatedAt).reversed()).toList();
        } else throw new BusinessException(400,"未知任务列表范围");
        return available.stream().limit(20).map(d->new TaskSummary(d.getId(),d.getTitle(),d.getCategory(),d.getStatus(),d.getReward(),d.getLocation(),d.getPublisherId(),d.getAcceptorId())).toList();
    }
    @Transactional(readOnly=true) public List<ToolAudit> auditList(Long actor) {
        return audits.findByActorIdOrderByCreatedAtDesc(actor,PageRequest.of(0,50));
    }
    @Transactional(readOnly=true) public Receipt get(Long actor,String id) {
        return view(intents.findById(id).filter(i->i.getActorId().equals(actor)).orElseThrow(()->new BusinessException(404,"执行请求不存在")));
    }
    private User lockUser(Long actor) {return users.lockById(actor).orElseThrow(()->new BusinessException(401,"账号不存在，请重新登录"));}
    private NeedContracts.Session confirmed(Long actor,long id,long revision) {
        var locked=sessions.lockOwned(id,actor).orElseThrow(()->new BusinessException(404,"本人草稿不存在"));
        if(locked.getRevision()!=revision) throw new BusinessException(409,"草稿已更新，请重新核对");
        var s=needs.get(actor,id);
        if(s.activeCategory()==null || s.routePending()) throw new BusinessException(422,"请先选择业务并确认草稿");
        var d=s.drafts().stream().filter(x->x.category()==s.activeCategory()).findFirst().orElseThrow();
        if(!Objects.equals(d.confirmedRevision(),d.revision()) || !d.assessment().ready()) throw new BusinessException(422,"必须先补全约定并确认当前草稿");
        String deadline=s.activeCategory()==NeedCategory.RUNNING?"latestDelivery":s.activeCategory()==NeedCategory.BORROW?"needBy":s.activeCategory()==NeedCategory.OTHER?"serviceEnd":null;
        if(deadline!=null && !LocalDateTime.parse(d.fields().get(deadline)).isAfter(LocalDateTime.now()))
            throw new BusinessException(422,"业务时间窗口已过，请修改草稿并重新确认");
        return s;
    }
    private void checkPublishCredit(User user) {
        if(user.getCreditScore()<40 || (user.getCreditScore()<60 && user.getRestrictedUntil()!=null && user.getRestrictedUntil().isAfter(LocalDateTime.now())))
            throw new BusinessException(403,"信用限制期间不能发布任务");
    }
    private void eligibility(Action action,User user,Demand demand,String reason) {
        if(action==Action.ACCEPT) {
            if(!"PENDING".equals(demand.getStatus())) throw new BusinessException(409,"任务不再等待接单");
            if(user.getId().equals(demand.getPublisherId())) throw new BusinessException(403,"不能接自己的任务");
            if(user.getCreditScore()<40) throw new BusinessException(403,"信用限制期间不能接单");
            if(reason!=null && !reason.isBlank()) throw new BusinessException(422,"接单不接受取消理由");
        } else {
            boolean publisher=user.getId().equals(demand.getPublisherId()),acceptor=user.getId().equals(demand.getAcceptorId());
            if(!publisher && !acceptor) throw new BusinessException(403,"只有任务双方才能取消");
            if(publisher) {
                if(!"PENDING".equals(demand.getStatus())) throw new BusinessException(409,"已有接单者，发布者不能单方面取消");
                if(Duration.between(demand.getCreatedAt(),LocalDateTime.now()).toMinutes()>30) throw new BusinessException(409,"已超过原系统取消窗口");
            } else {
                if(!Set.of("IN_PROGRESS","PENDING_CONFIRMATION").contains(demand.getStatus())) throw new BusinessException(409,"当前任务状态不能取消");
                if(reason==null || reason.isBlank()) throw new BusinessException(422,"接单者取消必须填写理由");
            }
        }
    }
    private String description(NeedContracts.Draft draft) {
        var lines=new ArrayList<String>();
        rules.catalog().stream().filter(c->c.category()==draft.category()).findFirst().orElseThrow().fields().forEach(field->{
            var value=draft.fields().get(field.key());if(value!=null && !value.isBlank()) lines.add(field.label()+"："+value);
        });
        if(!draft.contexts().isEmpty()) lines.add("特殊情境："+String.join("、",draft.contexts().stream().map(this::contextLabel).toList()));
        return String.join("\n",lines);
    }
    private String contextLabel(String key) {
        return Map.ofEntries(Map.entry("compatibility","规格待核实"),Map.entry("urgent","急需使用"),Map.entry("handoff","取还受限"),Map.entry("beginner","需要使用说明"),Map.entry("multiDay","跨天借还"),
            Map.entry("foundation","基础薄弱"),Map.entry("limited","时间紧张"),Map.entry("remote","时间或地点受限"),Map.entry("language","跨语言确认"),Map.entry("fragment","碎片化问题"),
            Map.entry("mobility","行动或搬运受限"),Map.entry("quiet","静默通知"),Map.entry("newcomer","不熟悉地点"),Map.entry("dropoff","指定点放置"),Map.entry("diagnosis","设备情况不清"),Map.entry("team","多人协助意向"),Map.entry("window","短时窗口"),Map.entry("scope","协助范围待明确")).getOrDefault(key,"待协商情境");
    }
    void audit(Long actor,String id,String action,String outcome,String message) {
        var a=new ToolAudit();a.setActorId(actor);a.setIntentId(id);a.setAction(action);a.setOutcome(outcome);a.setMessage(message);audits.saveAndFlush(a);
    }
    Receipt view(ToolIntent intent) {return new Receipt(intent.getId(),intent.getAction(),intent.getState(),intent.getExpiresAt(),read(intent.getPreviewJson(),Preview.class),intent.getResultJson()==null?null:read(intent.getResultJson(),DemandResponse.class),intent.getErrorCode(),intent.getErrorMessage(),intent.getSourceSessionId(),intent.getSourceRevision());}
    private String write(Object value) {try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException("Tool serialization failed",e);}}
    private <T> T read(String value,Class<T> type) {try{return json.readValue(value,type);}catch(Exception e){throw new IllegalStateException("Stored tool data invalid",e);}}
    private String hash(String value) {try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
