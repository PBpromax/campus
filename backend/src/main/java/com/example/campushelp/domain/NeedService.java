package com.example.campushelp.domain;

import com.example.campushelp.exception.BusinessException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
import static com.example.campushelp.domain.NeedContracts.*;

@Service @RequiredArgsConstructor @Transactional
public class NeedService {
    private final NeedSessionRepository sessions;
    private final NeedDraftRepository drafts;
    private final NeedMessageRepository messages;
    private final NeedRules rules;
    private final ObjectMapper json;

    public Session create(Long user) {
        NeedSession s=new NeedSession();s.setOwnerId(user);sessions.saveAndFlush(s);return view(s);
    }
    @Transactional(readOnly=true)
    public List<Session> list(Long user) { return sessions.findByOwnerIdOrderByUpdatedAtDesc(user).stream().map(this::view).toList(); }
    @Transactional(readOnly=true)
    public Session get(Long user,long id) {
        var s=sessions.findById(id).filter(x->x.getOwnerId().equals(user)).orElseThrow(()->new BusinessException(404,"会话不存在"));
        return view(s);
    }
    public Session select(Long user,long id,Select request) {
        var s=lock(user,id,request.revision());
        if(s.getActiveCategory()!=request.category()) {
            if(s.getActiveCategory()!=null) {
                var old=active(s);old.setConfirmedRevision(null);drafts.save(old);
            }
            var d=drafts.findBySessionIdAndCategory(id,request.category()).orElseGet(()->{
                var fresh=new NeedDraft();fresh.setSessionId(id);fresh.setCategory(request.category());return fresh;
            });
            if(d.getId()!=null) d.setRevision(d.getRevision()+1);
            d.setConfirmedRevision(null);drafts.saveAndFlush(d);
            s.setActiveCategory(request.category());
        }
        if(s.isRoutePending()) s.setRoutePending(false);
        changed(s);return view(s);
    }
    public Session edit(Long user,long id,Edit request) {
        var s=lock(user,id,request.revision());var d=active(s);
        rules.checkShape(d.getCategory(),request.fields(),request.contexts());
        var values=fields(d);var patch=new LinkedHashMap<>(request.fields());
        // Changing handoff must not silently reuse a previously negotiated location/consent.
        if(patch.containsKey("handoff") && !Objects.equals(values.get("handoff"),patch.get("handoff"))) {
            if(d.getCategory()==NeedCategory.BORROW) {
                if(!patch.containsKey("pickupPlace")) values.remove("pickupPlace");
                if(!patch.containsKey("deliveryConsent")) values.remove("deliveryConsent");
            }
            if(d.getCategory()==NeedCategory.RUNNING && !patch.containsKey("dropPlace")) values.remove("dropPlace");
        }
        values.putAll(patch);
        var contexts=request.contexts().stream().distinct().sorted().toList();
        if(!values.equals(fields(d)) || !contexts.equals(contexts(d))) {
            d.setFieldsJson(write(values));d.setContextsJson(write(contexts));
            d.setRevision(d.getRevision()+1);d.setConfirmedRevision(null);drafts.saveAndFlush(d);changed(s);
        }
        return view(s);
    }
    public Session intake(Long user,long id,Message request) {
        var s=lock(user,id,request.revision());append(s,null,request.text());
        if(s.getActiveCategory()!=null) { var d=active(s);d.setRevision(d.getRevision()+1);d.setConfirmedRevision(null);drafts.saveAndFlush(d); }
        s.setIntakeText(request.text());s.setRoutePending(true);
        // Suggestions never mutate category or draft fields.
        changed(s);return view(s);
    }
    public Session message(Long user,long id,Message request) {
        var s=lock(user,id,request.revision());var d=active(s);
        d.setRevision(d.getRevision()+1);d.setConfirmedRevision(null);drafts.saveAndFlush(d);
        append(s,d.getCategory(),request.text());changed(s);return view(s);
    }
    public Session assistant(Long user,long id,long revision,String text) {
        if(text.isBlank() || text.length()>4000) throw new BusinessException(422,"回复长度无效");
        var s=lock(user,id,revision);
        append(s,s.getActiveCategory(),text,"ASSISTANT");changed(s);return view(s);
    }
    public Session confirm(Long user,long id,Version request) {
        var s=lock(user,id,request.revision());var d=active(s);
        if(s.isRoutePending()) throw new BusinessException(409,"请先选择本次处理的业务");
        if(!rules.assess(d.getCategory(),fields(d),contexts(d)).ready()) throw new BusinessException(422,"存在缺失信息或约束冲突，不能确认");
        if(!Objects.equals(d.getConfirmedRevision(),d.getRevision())) {
            d.setConfirmedRevision(d.getRevision());drafts.saveAndFlush(d);changed(s);
        }
        return view(s); // Draft acknowledgement only: never creates a Demand or grants tool permission.
    }
    public void delete(Long user,long id,Version request) {
        lock(user,id,request.revision());messages.deleteBySessionId(id);drafts.deleteBySessionId(id);sessions.deleteById(id);
    }
    private void append(NeedSession s,NeedCategory category,String text) {
        append(s,category,text,"USER");
    }
    private void append(NeedSession s,NeedCategory category,String text,String role) {
        if(messages.countBySessionId(s.getId())>=200) throw new BusinessException(422,"单个会话最多200条消息，请新建会话");
        var m=new NeedMessage();m.setSessionId(s.getId());m.setCategory(category);m.setText(text);m.setRole(role);messages.saveAndFlush(m);
    }
    private NeedSession lock(Long user,long id,long revision) {
        var s=sessions.lockOwned(id,user).orElseThrow(()->new BusinessException(404,"会话不存在"));
        if(s.getRevision()!=revision) throw new BusinessException(409,"草稿已被更新，请重新加载后核对，未覆盖服务端内容");
        return s;
    }
    private NeedDraft active(NeedSession s) {
        if(s.getActiveCategory()==null) throw new BusinessException(409,"请先选择业务类别");
        return drafts.findBySessionIdAndCategory(s.getId(),s.getActiveCategory()).orElseThrow();
    }
    private void changed(NeedSession s) { s.setRevision(s.getRevision()+1);s.setUpdatedAt(LocalDateTime.now());sessions.saveAndFlush(s); }
    private Map<String,String> fields(NeedDraft d) { return read(d.getFieldsJson(),new TypeReference<LinkedHashMap<String,String>>(){}); }
    private List<String> contexts(NeedDraft d) { return read(d.getContextsJson(),new TypeReference<List<String>>(){}); }
    private String write(Object value) { try { return json.writeValueAsString(value); } catch(Exception e) { throw new IllegalStateException("Draft serialization failed",e); } }
    private <T> T read(String value,TypeReference<T> type) { try { return json.readValue(value,type); } catch(Exception e) { throw new IllegalStateException("Stored draft data invalid",e); } }
    private Session view(NeedSession s) {
        var views=drafts.findBySessionIdOrderById(s.getId()).stream().map(d->{
            var values=fields(d);var contexts=contexts(d);var provenance=new LinkedHashMap<String,String>();
            values.forEach((key,value)->provenance.put(key,value.isBlank()?"UNKNOWN":"USER_DECLARED"));
            return new Draft(d.getId(),d.getCategory(),d.getRevision(),d.getConfirmedRevision(),values,provenance,contexts,rules.assess(d.getCategory(),values,contexts));
        }).toList();
        var history=messages.findBySessionIdOrderById(s.getId()).stream().map(m->new Conversation(m.getId(),m.getCategory(),m.getRole()==null?"USER":m.getRole(),m.getText(),m.getCreatedAt())).toList();
        return new Session(s.getId(),s.getRevision(),s.getActiveCategory(),s.isRoutePending(),s.getIntakeText(),rules.suggest(s.getIntakeText()),views,history,s.getUpdatedAt());
    }
}
