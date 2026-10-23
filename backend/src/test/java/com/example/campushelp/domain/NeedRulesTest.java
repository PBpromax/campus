package com.example.campushelp.domain;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.io.ClassPathResource;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static com.example.campushelp.domain.NeedCategory.*;

class NeedRulesTest {
    private final NeedRules rules = new NeedRules(new ObjectMapper());
    NeedRulesTest() throws Exception { }
    record Scenario(String id,NeedCategory domain,String special,Map<String,String> values,String missing) { }
    static Stream<Scenario> samples() throws Exception {
        var mapper=new ObjectMapper().configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,false);
        try(var in=new ClassPathResource("domain/m1-scenarios.json").getInputStream()) {
            return mapper.readValue(in,new TypeReference<List<Scenario>>(){}).stream();
        }
    }
    @ParameterizedTest(name="{0}") @MethodSource("samples")
    void twentyM1SeedsRemainIncomplete(Scenario sample) {
        var result=rules.assess(sample.domain(),sample.values(),List.of(sample.special()));
        assertFalse(result.ready(),sample.id());
        assertTrue(result.missing().contains(sample.missing()),sample.id()+": "+result);
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.EnumSource(NeedCategory.class)
    void eachDomainAcceptsACompleteNonConflictingDraft(NeedCategory category) {
        Map<String,String> fields = switch(category) {
            case STUDY -> Map.of("context","y=-x，想弄懂符号变化");
            case BORROW -> Map.ofEntries(Map.entry("item","相机"),Map.entry("purpose","拍摄活动"),Map.entry("spec","索尼E卡口"),Map.entry("quantity","1"),
                Map.entry("needBy","2026-11-01T09:00"),Map.entry("useStart","2026-11-01T10:00"),Map.entry("useEnd","2026-11-01T12:00"),
                Map.entry("returnAt","2026-11-01T13:00"),Map.entry("pickupPlace","图书馆东门"),Map.entry("returnPlace","图书馆东门"),Map.entry("handoff","当面交接"));
            case RUNNING -> Map.of("taskKind","代取快递","item","书籍","quantity","1","pickupPlace","菜鸟驿站A区","destination","一号宿舍楼门口",
                "earliestPickup","2026-11-01T09:00","latestDelivery","2026-11-01T10:00","handoff","当面交付","notice","短消息","completionProof","当面确认");
            case OTHER -> Map.of("serviceType","活动布置","serviceGoal","布置社团展台","resourceRequirements","自带胶带",
                "serviceStart","2026-11-01T09:00","serviceEnd","2026-11-01T10:00","servicePlace","学生活动中心101",
                "communication","线下协助","completionCriteria","摆放两张桌子并张贴三张海报","consent","无需额外授权");
        };
        rules.checkShape(category,fields,List.of());
        assertTrue(rules.assess(category,fields,List.of()).ready());
    }
    @Test void smallStudyNeedsOnlyContextAndPreservesMath() {
        assertTrue(rules.assess(STUDY,Map.of("context","y=-x；第二步到第三步为什么变号？"),List.of("fragment")).ready());
    }
    @Test void unknownAndMixedTargetsAreOnlySuggestions() {
        assertEquals(List.of(),rules.suggest("帮我弄一下那个事"));
        assertEquals(Set.of(BORROW,RUNNING),new HashSet<>(rules.suggest("想借相机，再帮忙代取快递")));
    }
    @Test void quietAndDropoffRequirementsAreIndependent() {
        var r=rules.assess(RUNNING,Map.of("handoff","指定点放置","notice","电话","completionProof","当面确认","dropPlace","附近"),List.of("quiet"));
        assertTrue(r.conflicts().stream().anyMatch(s->s.contains("静默")));
        assertTrue(r.conflicts().stream().anyMatch(s->s.contains("放置")));
        assertTrue(r.conflicts().stream().anyMatch(s->s.contains("不明确")));
    }
    @Test void invalidCalendarCannotBeNormalized() {
        assertTrue(rules.assess(BORROW,Map.of("useStart","2026-02-30T10:00"),List.of()).conflicts().stream().anyMatch(s->s.contains("有效")));
    }
    @Test void borrowingDoesNotExtendLenderDeadline() {
        var r=rules.assess(BORROW,Map.of("returnAt","2026-11-04T18:00","returnDeadline","2026-11-03T18:00"),List.of());
        assertTrue(r.conflicts().stream().anyMatch(s->s.contains("returnDeadline")));
    }
    @Test void teamNeedsExplicitOrganization() {
        assertTrue(rules.assess(OTHER,Map.of("helperCount","3"),List.of("team")).missing().contains("splitMode"));
    }
    @Test void wrongDomainFieldsAndContextsRejected() {
        assertThrows(RuntimeException.class,()->rules.checkShape(STUDY,Map.of("returnAt","2026-11-04T18:00"),List.of()));
        assertThrows(RuntimeException.class,()->rules.checkShape(STUDY,Map.of(),List.of("quiet")));
    }
}
