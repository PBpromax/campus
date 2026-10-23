package com.example.campushelp.domain;

import com.example.campushelp.exception.BusinessException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;
import static com.example.campushelp.domain.NeedCategory.*;
import static com.example.campushelp.domain.NeedContracts.*;

@Component
public class NeedRules {
    private final List<Catalog> catalog;
    private static final Map<NeedCategory,List<String>> REQUIRED = Map.of(
        BORROW,List.of("item","purpose","spec","quantity","needBy","useStart","useEnd","returnAt","pickupPlace","returnPlace","handoff"),
        STUDY,List.of("context"),
        RUNNING,List.of("taskKind","item","quantity","pickupPlace","destination","earliestPickup","latestDelivery","handoff","notice","completionProof"),
        OTHER,List.of("serviceType","serviceGoal","resourceRequirements","serviceStart","serviceEnd","servicePlace","communication","completionCriteria","consent"));
    private static final Map<NeedCategory,Set<String>> CONTEXTS = Map.of(
        BORROW,Set.of("compatibility","urgent","handoff","beginner","multiDay"),
        STUDY,Set.of("foundation","limited","remote","language","fragment"),
        RUNNING,Set.of("mobility","quiet","newcomer","language","dropoff"),
        OTHER,Set.of("diagnosis","team","remote","window","scope"));
    private static final Map<NeedCategory,Pattern> ROUTES = Map.of(
        BORROW,Pattern.compile("借用|想借|借相机|借转接|归还|借期"),
        STUDY,Pattern.compile("学习|辅导|讲解|导数|求导|知识点|推导|题目|第[一二三四五0-9]+步|不懂|看不懂"),
        RUNNING,Pattern.compile("快递|代取|取件|代买|带饭|取餐|跑腿|代办|送.*到|pick.*(?:parcel|package)",Pattern.CASE_INSENSITIVE),
        OTHER,Pattern.compile("修电脑|电脑.*(?:连不上|故障)|设备排查|软件.*(?:设置|安装)|活动.*(?:协助|布置|帮忙)|协助活动|整理.*资料"));

    public NeedRules(ObjectMapper mapper) throws IOException {
        try (var input = new ClassPathResource("domain/need-fields.json").getInputStream()) {
            catalog = mapper.readValue(input,new TypeReference<List<Catalog>>() { });
        }
    }
    public List<Catalog> catalog() { return catalog; }
    public List<NeedCategory> suggest(String text) {
        return Arrays.stream(NeedCategory.values()).filter(c->ROUTES.get(c).matcher(text).find()).toList();
    }
    public void checkShape(NeedCategory category,Map<String,String> fields,List<String> contexts) {
        var known = definitions(category).stream().map(Field::key).toList();
        if (!known.containsAll(fields.keySet())) throw new BusinessException(400,"包含当前业务以外的字段");
        if (!CONTEXTS.get(category).containsAll(contexts)) throw new BusinessException(400,"未知的业务情境");
    }
    private List<Field> definitions(NeedCategory category) {
        return catalog.stream().filter(c->c.category()==category).findFirst().orElseThrow().fields();
    }
    public Assessment assess(NeedCategory c,Map<String,String> fields,List<String> contexts) {
        var needed = new LinkedHashSet<>(REQUIRED.get(c));
        var risks = new ArrayList<String>();
        if(c==BORROW) {
            if("需要送达".equals(fields.get("handoff"))) needed.add("deliveryConsent");
            if(contexts.contains("beginner") || "需要简短说明".equals(fields.get("support"))) needed.addAll(List.of("support","accessories"));
        }
        if(c==STUDY) {
            if(contexts.contains("foundation")) needed.addAll(List.of("topic","depth"));
            if(contexts.contains("limited")) needed.addAll(List.of("priority","availableTime"));
            if(contexts.contains("remote")) needed.add("communication");
            if(contexts.contains("language")) needed.add("language");
        }
        if(c==RUNNING) {
            if("指定点放置".equals(fields.get("handoff"))) needed.add("dropPlace");
            if(contexts.contains("mobility")) needed.add("handling");
            if(contexts.contains("language")) needed.add("language");
            if("代买物品".equals(fields.get("taskKind"))) needed.add("costLimit");
            if(contexts.contains("quiet") && "电话".equals(fields.get("notice"))) risks.add("静默约束与电话通知冲突");
            if("指定点放置".equals(fields.get("handoff")) && present(fields,"completionProof") && !"放置照片与通知".equals(fields.get("completionProof"))) risks.add("放置交付需要约定放置照片与通知");
            if("当面交付".equals(fields.get("handoff")) && present(fields,"completionProof") && !"当面确认".equals(fields.get("completionProof"))) risks.add("当面交付需要约定当面确认");
        }
        if(c==OTHER) {
            if(contexts.contains("team")) needed.add("helperCount");
            if(integer(fields.get("helperCount"))>1) needed.add("splitMode");
            if(fields.getOrDefault("completionCriteria","").matches("帮忙|随便|待定|不清楚|不知道")) risks.add("完成标准仍含糊");
            if(Pattern.compile("代考|作弊|盗号|破解密码").matcher(fields.getOrDefault("serviceGoal","")).find()) risks.add("协助目标超出互助授权范围");
        }
        for(var def:definitions(c)) {
            String value=fields.get(def.key());
            if(value==null || value.isBlank()) continue;
            if(def.type().equals("select") && !def.options().contains(value)) risks.add(def.label()+"不在允许选项中");
            if(def.type().equals("datetime-local") && time(value)==null) risks.add(def.label()+"需要有效的具体日期和时间");
            if(Set.of("quantity","helperCount").contains(def.key()) && (integer(value)<1 || integer(value)>99)) risks.add(def.label()+"必须为1—99的整数");
            if(def.key().equals("costLimit")) {
                try { if(new BigDecimal(value).signum()<=0) risks.add("代买预算必须大于0"); }
                catch(NumberFormatException e) { risks.add("代买预算必须是有效金额"); }
            }
        }
        var places=c==BORROW?List.of("pickupPlace","returnPlace"):c==RUNNING?List.of("pickupPlace","destination","dropPlace"):c==OTHER?List.of("servicePlace"):List.<String>of();
        for(String key:places) {
            if(key.equals("dropPlace") && !"指定点放置".equals(fields.get("handoff"))) continue;
            if(present(fields,key) && Pattern.compile("某个|随便|附近|不知道|不确定|待定|哪里都行").matcher(fields.get(key)).find()) risks.add(key+"位置或渠道不明确");
        }
        if(c==BORROW) {
            if(fields.getOrDefault("spec","").matches("不知道|不确定|随便|待定")) risks.add("物品规格仍未知，不能假定适用");
            ordered(fields,"needBy","useStart",true,risks);
            ordered(fields,"useStart","useEnd",false,risks);
            ordered(fields,"useEnd","returnAt",true,risks);
            ordered(fields,"returnAt","returnDeadline",true,risks);
        }
        if(c==RUNNING) ordered(fields,"earliestPickup","latestDelivery",false,risks);
        if(c==OTHER) ordered(fields,"serviceStart","serviceEnd",false,risks);
        var missing=needed.stream().filter(k->!present(fields,k)).toList();
        return new Assessment(missing.isEmpty() && risks.isEmpty(),missing,risks);
    }
    private static boolean present(Map<String,String> fields,String key) { return fields.get(key)!=null && !fields.get(key).isBlank(); }
    private static int integer(String value) { try { return value!=null && value.matches("[0-9]{1,2}")?Integer.parseInt(value):-1; } catch(NumberFormatException e) { return -1; } }
    private static LocalDateTime time(String value) {
        try { return value!=null && value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}")?LocalDateTime.parse(value):null; }
        catch(java.time.DateTimeException e) { return null; }
    }
    private static void ordered(Map<String,String> fields,String first,String last,boolean equal,List<String> risks) {
        var a=time(fields.get(first));var b=time(fields.get(last));
        if(a!=null && b!=null && (a.isAfter(b) || (!equal && a.equals(b)))) risks.add(first+"与"+last+"时间约束冲突");
    }
}
