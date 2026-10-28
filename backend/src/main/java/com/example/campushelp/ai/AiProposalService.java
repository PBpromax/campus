package com.example.campushelp.ai;

import com.example.campushelp.domain.*;
import com.example.campushelp.exception.BusinessException;
import com.fasterxml.jackson.databind.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import static com.example.campushelp.domain.NeedContracts.*;

@Service @RequiredArgsConstructor
public class AiProposalService {
    private final ObjectMapper json;
    private final NeedRules rules;
    private final NeedService needs;
    public record Proposal(String token,long sessionId,long revision,NeedCategory category,Map<String,String> fields,
        List<NeedCategory> candidates,Assessment assessment,List<String> warnings) { }
    private record Stored(Long owner,Instant expires,Proposal proposal) { }
    private final Map<String,Stored> pending=new ConcurrentHashMap<>();
    public Proposal inspect(Session s,String arguments) throws Exception {
        var node=json.readTree(arguments);
        if(!node.isObject() || node.size()!=2 || !node.path("fields").isObject() || !node.path("candidates").isArray())
            throw new BusinessException(422,"模型建议格式无效");
        var fields=new LinkedHashMap<String,String>();
        var iterator=node.get("fields").fields();
        while(iterator.hasNext()) {
            var entry=iterator.next();
            if(!entry.getValue().isTextual() || entry.getValue().asText().length()>6000) throw new BusinessException(422,"模型字段格式无效");
            fields.put(entry.getKey(),entry.getValue().asText());
        }
        if(fields.size()>30 || fields.containsKey("consent") || fields.containsKey("deliveryConsent"))
            throw new BusinessException(422,"模型不能代替用户填写授权条件");
        var candidates=new LinkedHashSet<NeedCategory>();
        if(node.get("candidates").size()>4) throw new BusinessException(422,"分类建议超限");
        for(var candidate:node.get("candidates")) {
            if(!candidate.isTextual()) throw new BusinessException(422,"分类建议无效");
            try { candidates.add(NeedCategory.valueOf(candidate.asText())); }
            catch(IllegalArgumentException e) { throw new BusinessException(422,"分类建议无效"); }
        }
        var warnings=new ArrayList<String>();
        if(fields.containsKey("helperCount")) {
            String proposed=fields.get("helperCount");
            boolean supported=s.drafts().stream().filter(d->d.category()==s.activeCategory()).anyMatch(d->proposed.equals(d.fields().get("helperCount")));
            var countPattern=java.util.regex.Pattern.compile("([0-9]{1,2}|[一二两三四五六七八九十])\\s*(?:位|名|个)?\\s*(?:同学|人|帮手|协助者)");
            for(var message:s.messages()) {
                if(!message.role().equals("USER") || (message.category()!=null && message.category()!=s.activeCategory())) continue;
                var match=countPattern.matcher(message.text());
                while(match.find()) {
                    String number=match.group(1);
                    if(!number.matches("[0-9]+")) number=String.valueOf("一二三四五六七八九十".indexOf(number)+1);
                    if(match.group(1).equals("两")) number="2";
                    if(number.equals(proposed)) supported=true;
                }
            }
            if(!supported) {fields.remove("helperCount");warnings.add("你还没有明确说明协助人数，AI提出的人数已移除，请继续说明或在完整表单填写。");}
        }
        Assessment assessment=new Assessment(false,List.of(),List.of());
        if(s.activeCategory()==null || s.routePending()) {
            if(!fields.isEmpty()) throw new BusinessException(422,"请先由用户明确选择业务，再采纳字段建议");
        } else {
            var d=s.drafts().stream().filter(x->x.category()==s.activeCategory()).findFirst().orElseThrow();
            rules.checkShape(s.activeCategory(),fields,d.contexts());
            var merged=new LinkedHashMap<>(d.fields());merged.putAll(fields);
            assessment=rules.assess(s.activeCategory(),merged,d.contexts());
        }
        return new Proposal("",s.id(),s.revision(),s.activeCategory(),Collections.unmodifiableMap(fields),List.copyOf(candidates),assessment,List.copyOf(warnings));
    }
    public Proposal offer(Long owner,Session s,String arguments) throws Exception { return remember(owner,s,inspect(s,arguments)); }
    Proposal remember(Long owner,Session s,Proposal inspected) {
        if(inspected.sessionId()!=s.id() || inspected.category()!=s.activeCategory()) throw new BusinessException(409,"需求已更新，请重新核对");
        pending.entrySet().removeIf(entry->entry.getValue().expires().isBefore(Instant.now()));
        if(pending.size()>=256) throw new BusinessException(429,"待核对建议过多，请稍后再试");
        var proposal=new Proposal(UUID.randomUUID().toString(),s.id(),s.revision(),s.activeCategory(),inspected.fields(),inspected.candidates(),inspected.assessment(),inspected.warnings());
        pending.put(proposal.token(),new Stored(owner,Instant.now().plusSeconds(300),proposal));return proposal;
    }
    public synchronized Session apply(Long owner,long sessionId,String token,Version request) {
        var stored=pending.get(token);
        if(stored==null || !stored.owner().equals(owner) || stored.proposal().sessionId()!=sessionId)
            throw new BusinessException(404,"建议不存在或已失效");
        var p=stored.proposal();
        if(stored.expires().isBefore(Instant.now())) {pending.remove(token);throw new BusinessException(409,"建议已过期，请重新对话");}
        if(request.revision()!=p.revision()) throw new BusinessException(409,"建议版本已过期");
        var s=needs.get(owner,sessionId);
        if(s.revision()!=p.revision() || s.activeCategory()!=p.category() || s.routePending())
            throw new BusinessException(409,"草稿已变更，请重新对话核对");
        if(p.fields().isEmpty()) throw new BusinessException(422,"没有可采纳的字段，请手动选择业务或继续澄清");
        var d=s.drafts().stream().filter(x->x.category()==s.activeCategory()).findFirst().orElseThrow();
        var result=needs.edit(owner,sessionId,new Edit(request.revision(),p.fields(),d.contexts()));
        pending.remove(token);return result;
    }
}
