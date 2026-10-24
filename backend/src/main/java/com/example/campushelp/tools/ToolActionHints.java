package com.example.campushelp.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.regex.Pattern;

@Component @RequiredArgsConstructor
public class ToolActionHints {
    private final ObjectMapper json;
    public record Hint(String action,Long demandId,String explanation) { }
    public Hint validate(String arguments,String originalText) throws Exception {
        var n=json.readTree(arguments);
        if(!n.isObject() || n.size()!=2 || !n.path("action").isTextual() || !n.has("demandId")) throw new IllegalArgumentException("Invalid hint");
        var action=ToolContracts.Action.valueOf(n.get("action").asText());
        String intent=switch(action) {case PUBLISH->"发布|发出去|publish|post";case ACCEPT->"接单|接这个|我来帮|accept";case CANCEL->"取消|撤销任务|cancel";};
        if(!Pattern.compile(intent,Pattern.CASE_INSENSITIVE).matcher(originalText).find()) throw new IllegalArgumentException("Intent not explicit");
        String negation=switch(action) {case PUBLISH->"不要发布|别发布|不想发布|先别发|暂不发布|不需要发布";case ACCEPT->"不要接|别接|不接单|不想接";case CANCEL->"不要取消|别取消|不取消";};
        if(Pattern.compile(negation).matcher(originalText).find()) throw new IllegalArgumentException("Negative intent");
        Long target=null;
        if(!n.get("demandId").isNull()) {
            if(!n.get("demandId").isIntegralNumber() || !n.get("demandId").canConvertToLong()) throw new IllegalArgumentException("Invalid id");
            target=n.get("demandId").asLong();
            if(target<=0 || !Pattern.compile("(?<![0-9])"+target+"(?![0-9])").matcher(originalText).find()) throw new IllegalArgumentException("Task id not explicit");
        }
        if(action==ToolContracts.Action.PUBLISH && target!=null) throw new IllegalArgumentException("Publish cannot target existing task");
        return new Hint(action.name(),target,"仅为操作建议；仍需核对实际任务、准备确认卡并由你明确执行。");
    }
}
