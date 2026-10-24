package com.example.campushelp.tools;

import com.example.campushelp.domain.*;
import com.example.campushelp.entity.*;
import com.example.campushelp.exception.BusinessException;
import com.example.campushelp.repository.*;
import com.example.campushelp.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ActiveProfiles;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static com.example.campushelp.tools.ToolContracts.*;
import static com.example.campushelp.domain.NeedContracts.*;

@SpringBootTest @ActiveProfiles("test")
class ToolIntegrationTest {
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired ToolService tools;
    @Autowired ToolTransactions transactions;
    @Autowired ToolIntentRepository intents;
    @Autowired ToolAuditRepository audits;
    @Autowired NeedService needs;
    @Autowired NeedMessageRepository messages;
    @Autowired NeedDraftRepository drafts;
    @Autowired NeedSessionRepository sessions;
    @Autowired DemandRepository demands;
    @Autowired UserRepository users;
    @Autowired NotificationRepository notifications;
    @Autowired DemandService demandService;
    @SpyBean NotificationService notificationService;
    Long publisher,helper,other;
    @BeforeEach void setup() {
        reset(notificationService);audits.deleteAll();intents.deleteAll();messages.deleteAll();drafts.deleteAll();sessions.deleteAll();notifications.deleteAll();demands.deleteAll();users.deleteAll();
        publisher=user("publisher");helper=user("helper");other=user("other");
    }
    private Long user(String name) {var u=new User();u.setUsername(name+UUID.randomUUID().toString().substring(0,8));u.setPassword("fixture-hash");u.setName(name);return users.saveAndFlush(u).getId();}
    private Publication pub() {return new Publication("测试互助",new BigDecimal("2.00"),"图书馆东门",LocalDateTime.now().plusDays(30));}
    private Session source(NeedCategory category) {
        var s=needs.create(publisher);s=needs.select(publisher,s.id(),new Select(s.revision(),category));
        Map<String,String> values=switch(category) {
            case STUDY -> Map.of("context","y=-x，想弄懂变号");
            case RUNNING -> Map.of("taskKind","代取快递","item","书","quantity","1","pickupPlace","驿站A区","destination","一号宿舍门口",
                "earliestPickup","2026-11-01T09:00","latestDelivery","2026-11-01T10:00","handoff","当面交付","notice","短消息","completionProof","当面确认");
            case BORROW -> Map.ofEntries(Map.entry("item","相机"),Map.entry("purpose","活动拍摄"),Map.entry("spec","E卡口"),Map.entry("quantity","1"),
                Map.entry("needBy","2026-11-01T09:00"),Map.entry("useStart","2026-11-01T10:00"),Map.entry("useEnd","2026-11-01T12:00"),
                Map.entry("returnAt","2026-11-01T13:00"),Map.entry("pickupPlace","图书馆东门"),Map.entry("returnPlace","图书馆东门"),Map.entry("handoff","当面交接"));
            case OTHER -> Map.of("serviceType","活动布置","serviceGoal","摆放桌椅","resourceRequirements","自带胶带","serviceStart","2026-11-01T09:00",
                "serviceEnd","2026-11-01T10:00","servicePlace","活动中心101","communication","线下协助","completionCriteria","摆放2张桌子","consent","无需额外授权");
        };
        var futureValues=new LinkedHashMap<String,String>();
        values.forEach((key,value)->futureValues.put(key,value.replace("2026-11-01",java.time.LocalDate.now().plusDays(7).toString())));
        s=needs.edit(publisher,s.id(),new Edit(s.revision(),futureValues,List.of()));return needs.confirm(publisher,s.id(),new Version(s.revision()));
    }
    private Prepare publish(Session s,String key) {return new Prepare(Action.PUBLISH,key,s.id(),s.revision(),null,null,pub());}
    private Prepare operation(Action action,long id,String reason) {return new Prepare(action,UUID.randomUUID().toString(),null,null,id,reason,null);}
    private Receipt published() {var s=source(NeedCategory.STUDY);var p=tools.prepare(publisher,publish(s,UUID.randomUUID().toString()));return tools.execute(publisher,p.id(),new Confirm(true));}
    private int failureCode(org.junit.jupiter.api.function.Executable action) {return assertThrows(BusinessException.class,action).getCode();}

    @ParameterizedTest @EnumSource(NeedCategory.class)
    void fourDomainsPublishOnceWithImmutableReceipt(NeedCategory category) {
        var s=source(category);var request=publish(s,UUID.randomUUID().toString());
        var prepared=tools.prepare(publisher,request);assertEquals(0,demands.count());
        assertEquals(prepared.id(),tools.prepare(publisher,request).id());
        var first=tools.execute(publisher,prepared.id(),new Confirm(true));
        var replay=tools.execute(publisher,prepared.id(),new Confirm(true));
        assertEquals(first.result().getId(),replay.result().getId());assertEquals(1,demands.count());
        assertEquals(category.name(),first.result().getCategory());assertTrue(first.preview().description().contains("："));
        assertEquals(1,audits.findAll().stream().filter(a->a.getOutcome().equals("SUCCEEDED")).count());
        assertEquals(409,failureCode(()->tools.prepare(publisher,publish(s,UUID.randomUUID().toString()))));
    }
    @Test void noConfirmationNoExecutionAndRevokeIsSafe() {
        var s=source(NeedCategory.STUDY);var p=tools.prepare(publisher,publish(s,UUID.randomUUID().toString()));
        assertEquals(422,failureCode(()->tools.execute(publisher,p.id(),new Confirm(false))));assertEquals(0,demands.count());
        assertEquals("PREPARED",transactions.get(publisher,p.id()).state());
        tools.revoke(publisher,p.id());assertEquals(409,failureCode(()->tools.execute(publisher,p.id(),new Confirm(true))));assertEquals(0,demands.count());
    }
    @Test void changedDraftAndExpiredIntentCannotExecute() {
        var s=source(NeedCategory.STUDY);var p=tools.prepare(publisher,publish(s,UUID.randomUUID().toString()));
        needs.edit(publisher,s.id(),new Edit(s.revision(),Map.of("context","新问题"),List.of()));
        assertEquals(409,failureCode(()->tools.execute(publisher,p.id(),new Confirm(true))));assertEquals("FAILED",transactions.get(publisher,p.id()).state());
        var another=source(NeedCategory.STUDY);var q=tools.prepare(publisher,publish(another,UUID.randomUUID().toString()));
        var expired=intents.findById(q.id()).orElseThrow();expired.setExpiresAt(LocalDateTime.now().minusSeconds(1));intents.saveAndFlush(expired);
        assertEquals(409,failureCode(()->tools.execute(publisher,q.id(),new Confirm(true))));assertEquals(0,demands.count());
    }
    @Test void foreignUserCannotPublishOrPoisonOwnersIntent() {
        var s=source(NeedCategory.STUDY);
        assertEquals(404,failureCode(()->tools.prepare(other,publish(s,UUID.randomUUID().toString()))));
        var p=tools.prepare(publisher,publish(s,UUID.randomUUID().toString()));
        assertEquals(404,failureCode(()->tools.execute(other,p.id(),new Confirm(true))));
        assertEquals("PREPARED",transactions.get(publisher,p.id()).state());assertTrue(transactions.list(other).isEmpty());
    }
    @Test void reusedRequestKeyWithDifferentPayloadRejected() {
        var s=source(NeedCategory.STUDY);String key=UUID.randomUUID().toString();tools.prepare(publisher,publish(s,key));
        var different=new Prepare(Action.PUBLISH,key,s.id(),s.revision(),null,null,new Publication("改标题",BigDecimal.ONE,"图书馆",LocalDateTime.now().plusDays(2)));
        assertEquals(409,failureCode(()->tools.prepare(publisher,different)));assertEquals(0,demands.count());
    }
    @Test void unconfirmedDraftAndInvalidPublicationAreRejected() {
        var s=needs.create(publisher);s=needs.select(publisher,s.id(),new Select(s.revision(),NeedCategory.STUDY));final var unconfirmed=s;
        assertEquals(422,failureCode(()->tools.prepare(publisher,publish(unconfirmed,UUID.randomUUID().toString()))));
        var ready=source(NeedCategory.STUDY);
        var invalid=new Prepare(Action.PUBLISH,UUID.randomUUID().toString(),ready.id(),ready.revision(),null,null,new Publication("标题",BigDecimal.ZERO,"位置",LocalDateTime.now().plusDays(1)));
        assertEquals(422,failureCode(()->tools.prepare(publisher,invalid)));assertEquals(0,demands.count());
    }
    @Test void longAgreementIsNotSilentlyTruncated() {
        var s=source(NeedCategory.STUDY);s=needs.edit(publisher,s.id(),new Edit(s.revision(),Map.of("context","长".repeat(501)),List.of()));s=needs.confirm(publisher,s.id(),new Version(s.revision()));final var ready=s;
        assertEquals(422,failureCode(()->tools.prepare(publisher,publish(ready,UUID.randomUUID().toString()))));assertEquals(0,demands.count());
    }
    @Test void sameDraftTwoDifferentIntentsStillOnlyPublishOnce() {
        var s=source(NeedCategory.STUDY);var a=tools.prepare(publisher,publish(s,UUID.randomUUID().toString()));var b=tools.prepare(publisher,publish(s,UUID.randomUUID().toString()));
        tools.execute(publisher,a.id(),new Confirm(true));assertEquals(409,failureCode(()->tools.execute(publisher,b.id(),new Confirm(true))));assertEquals(1,demands.count());
    }
    @Test void rolesAndCreditRestrictionsAreEnforced() {
        var task=published().result();
        assertEquals(403,failureCode(()->tools.prepare(publisher,operation(Action.ACCEPT,task.getId(),null))));
        assertEquals(403,failureCode(()->tools.prepare(other,operation(Action.CANCEL,task.getId(),"无权取消"))));
        var blocked=users.findById(helper).orElseThrow();blocked.setCreditScore(39);users.saveAndFlush(blocked);
        assertEquals(403,failureCode(()->tools.prepare(helper,operation(Action.ACCEPT,task.getId(),null))));
    }
    @Test void acceptorCancellationDeductsCreditExactlyOnce() {
        var id=published().result().getId();var accept=tools.prepare(helper,operation(Action.ACCEPT,id,null));tools.execute(helper,accept.id(),new Confirm(true));
        assertEquals(422,failureCode(()->tools.prepare(helper,operation(Action.CANCEL,id,""))));
        assertEquals(409,failureCode(()->tools.prepare(publisher,operation(Action.CANCEL,id,"不能单方面取消"))));
        var cancel=tools.prepare(helper,operation(Action.CANCEL,id,"无法按时完成"));
        assertTrue(cancel.preview().consequences().stream().anyMatch(c->c.contains("5分")));
        var result=tools.execute(helper,cancel.id(),new Confirm(true));tools.execute(helper,cancel.id(),new Confirm(true));
        assertEquals("CANCELLED",result.result().getStatus());assertEquals(95,users.findById(helper).orElseThrow().getCreditScore());
    }
    @Test void competingAcceptorsOnlyOneSucceeds() throws Exception {
        var id=published().result().getId();var a=tools.prepare(helper,operation(Action.ACCEPT,id,null));var b=tools.prepare(other,operation(Action.ACCEPT,id,null));
        var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            var x=pool.submit(()->{start.await();try{tools.execute(helper,a.id(),new Confirm(true));return 200;}catch(BusinessException e){return e.getCode();}});
            var y=pool.submit(()->{start.await();try{tools.execute(other,b.id(),new Confirm(true));return 200;}catch(BusinessException e){return e.getCode();}});
            start.countDown();var codes=List.of(x.get(10,TimeUnit.SECONDS),y.get(10,TimeUnit.SECONDS));assertEquals(1,codes.stream().filter(c->c==200).count());assertTrue(codes.contains(409));
            assertEquals("IN_PROGRESS",demands.findById(id).orElseThrow().getStatus());
            assertEquals(1,notifications.findAll().stream().filter(n->"ACCEPT".equals(n.getType())).count());
        } finally {pool.shutdownNow();}
    }
    @Test void failureRollsBackTaskAndNotificationButKeepsFailureAudit() {
        var id=published().result().getId();var p=tools.prepare(helper,operation(Action.ACCEPT,id,null));
        doThrow(new IllegalStateException("fixture notification failure")).when(notificationService).createNotification(anyLong(),anyString(),anyString(),eq("ACCEPT"),eq(id));
        assertEquals(503,failureCode(()->tools.execute(helper,p.id(),new Confirm(true))));
        assertEquals("PENDING",demands.findById(id).orElseThrow().getStatus());assertNull(demands.findById(id).orElseThrow().getAcceptorId());
        assertEquals(0,notifications.count());assertEquals("FAILED",transactions.get(helper,p.id()).state());
        assertTrue(transactions.auditList(helper).stream().anyMatch(a->a.getOutcome().equals("FAILED")));
        assertEquals(503,failureCode(()->tools.execute(helper,p.id(),new Confirm(true))));assertEquals(100,users.findById(helper).orElseThrow().getCreditScore());
    }
    @Test void cancelledOrChangedTaskInvalidatesPreparedAccept() {
        var id=published().result().getId();var a=tools.prepare(helper,operation(Action.ACCEPT,id,null));
        var cancel=tools.prepare(publisher,operation(Action.CANCEL,id,"撤回求助"));tools.execute(publisher,cancel.id(),new Confirm(true));
        assertEquals(409,failureCode(()->tools.execute(helper,a.id(),new Confirm(true))));assertEquals("CANCELLED",demands.findById(id).orElseThrow().getStatus());
    }
    @Test void publisherCancellationWindowIsCheckedAgain() {
        var id=published().result().getId();var p=tools.prepare(publisher,operation(Action.CANCEL,id,null));
        jdbc.update("update demands set created_at=? where id=?",java.sql.Timestamp.valueOf(LocalDateTime.now().minusMinutes(31)),id);
        assertEquals(409,failureCode(()->tools.execute(publisher,p.id(),new Confirm(true))));assertEquals("PENDING",demands.findById(id).orElseThrow().getStatus());
    }
    @Test void expiredBusinessDeadlineAndUnsupportedSplitCannotPublish() {
        var s=source(NeedCategory.RUNNING);
        s=needs.edit(publisher,s.id(),new Edit(s.revision(),Map.of("earliestPickup","2020-01-01T09:00","latestDelivery","2020-01-01T10:00"),List.of()));
        s=needs.confirm(publisher,s.id(),new Version(s.revision()));final var past=s;
        assertEquals(422,failureCode(()->tools.prepare(publisher,publish(past,UUID.randomUUID().toString()))));
        var otherSource=source(NeedCategory.OTHER);
        otherSource=needs.edit(publisher,otherSource.id(),new Edit(otherSource.revision(),Map.of("helperCount","2","splitMode","希望拆分为单人任务"),List.of("team")));
        otherSource=needs.confirm(publisher,otherSource.id(),new Version(otherSource.revision()));final var split=otherSource;
        assertEquals(422,failureCode(()->tools.prepare(publisher,publish(split,UUID.randomUUID().toString()))));
    }
    @Test void legacyConcurrentAcceptAlsoCannotOverwriteWinner() throws Exception {
        var id=published().result().getId();var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            var x=pool.submit(()->{start.await();try{demandService.acceptDemand(id,helper);return true;}catch(RuntimeException e){return false;}});
            var y=pool.submit(()->{start.await();try{demandService.acceptDemand(id,other);return true;}catch(RuntimeException e){return false;}});
            start.countDown();int wins=(x.get(10,TimeUnit.SECONDS)?1:0)+(y.get(10,TimeUnit.SECONDS)?1:0);assertEquals(1,wins);
            assertEquals(1,notifications.findAll().stream().filter(n->"ACCEPT".equals(n.getType())).count());
        } finally {pool.shutdownNow();}
    }
}
