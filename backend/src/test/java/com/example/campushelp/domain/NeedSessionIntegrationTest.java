package com.example.campushelp.domain;

import com.example.campushelp.util.JwtUtil;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class NeedSessionIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtUtil jwt;
    @Autowired NeedSessionRepository sessions;
    @Autowired NeedDraftRepository drafts;
    @Autowired NeedMessageRepository messages;
    private String owner,other;
    @BeforeEach void setup() {
        messages.deleteAll();drafts.deleteAll();sessions.deleteAll();
        owner=jwt.generateToken(8001L);other=jwt.generateToken(8002L);
    }
    private JsonNode call(MockHttpServletRequestBuilder req,Object body,String token,int expected) throws Exception {
        req.header("Authorization","Bearer "+token);
        if(body!=null) req.contentType("application/json").content(json.writeValueAsString(body));
        var r=mvc.perform(req).andReturn();
        assertEquals(expected,r.getResponse().getStatus(),r.getResponse().getContentAsString());
        return json.readTree(r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).get("data");
    }
    private JsonNode create() throws Exception { return call(post("/api/need-sessions"),null,owner,200); }
    private long rev(JsonNode s) { return s.get("revision").asLong(); }
    private JsonNode select(JsonNode s,String category) throws Exception {
        return call(put("/api/need-sessions/"+s.get("id").asLong()+"/category"),Map.of("revision",rev(s),"category",category),owner,200);
    }
    @Test void authenticationOwnershipAndCategoryAllowlist() throws Exception {
        assertEquals(403,mvc.perform(get("/api/need-sessions")).andReturn().getResponse().getStatus());
        var s=create();var id=s.get("id").asLong();
        call(get("/api/need-sessions/"+id),null,other,404);
        call(put("/api/need-sessions/"+id+"/category"),Map.of("revision",rev(s),"category","AUTO"),owner,400);
        call(get("/api/need-sessions"),null,other,200).forEach(x->fail("Leaked session"));
        call(put("/api/need-sessions/"+id+"/category"),Map.of("revision",rev(s),"category","STUDY"),other,404);
    }
    @Test void fourDraftsStayIndependentAndSurviveReload() throws Exception {
        var s=create();var id=s.get("id").asLong();
        var entries=Map.of("BORROW",Map.of("item","相机"),"STUDY",Map.of("context","第二步为什么取负号"),"RUNNING",Map.of("item","快递"),"OTHER",Map.of("serviceGoal","布置活动桌椅"));
        for(String category:List.of("BORROW","STUDY","RUNNING","OTHER")) {
            s=select(s,category);
            s=call(put("/api/need-sessions/"+id+"/draft"),Map.of("revision",rev(s),"fields",entries.get(category),"contexts",List.of()),owner,200);
        }
        s=call(get("/api/need-sessions/"+id),null,owner,200);
        assertEquals(4,s.get("drafts").size());
        s=select(s,"STUDY");var study=find(s,"STUDY");
        assertEquals("第二步为什么取负号",study.at("/fields/context").asText());
        assertFalse(study.get("fields").has("item"));
        assertTrue(study.at("/assessment/ready").asBoolean());
    }
    private JsonNode find(JsonNode s,String category) { for(var d:s.get("drafts")) if(d.get("category").asText().equals(category))return d;throw new AssertionError(); }
    @Test void confirmEditsRestoreAndMessagesInvalidateOldConsent() throws Exception {
        var s=select(create(),"STUDY");var id=s.get("id").asLong();
        s=call(put("/api/need-sessions/"+id+"/draft"),Map.of("revision",rev(s),"fields",Map.of("context","y=-x"),"contexts",List.of("fragment")),owner,200);
        s=call(post("/api/need-sessions/"+id+"/confirm"),Map.of("revision",rev(s)),owner,200);
        assertFalse(find(s,"STUDY").get("confirmedRevision").isNull());
        s=call(post("/api/need-sessions/"+id+"/messages"),Map.of("revision",rev(s),"text","这里的负号呢？"),owner,200);
        assertTrue(find(s,"STUDY").get("confirmedRevision").isNull());
        s=call(post("/api/need-sessions/"+id+"/confirm"),Map.of("revision",rev(s)),owner,200);
        s=select(s,"RUNNING");s=select(s,"STUDY");
        assertTrue(find(s,"STUDY").get("confirmedRevision").isNull());
        assertEquals("这里的负号呢？",s.get("messages").get(0).get("text").asText());
        s=call(post("/api/need-sessions/"+id+"/confirm"),Map.of("revision",rev(s)),owner,200);
        s=call(put("/api/need-sessions/"+id+"/draft"),Map.of("revision",rev(s),"fields",Map.of("context","y=x"),"contexts",List.of("fragment")),owner,200);
        assertTrue(find(s,"STUDY").get("confirmedRevision").isNull());
    }
    @Test void routingNeverAutoSwitchesOrGuessesOther() throws Exception {
        var s=select(create(),"BORROW");var id=s.get("id").asLong();
        s=call(post("/api/need-sessions/"+id+"/intake"),Map.of("revision",rev(s),"text","借相机加代取快递"),owner,200);
        assertEquals(2,s.get("candidates").size());assertEquals("BORROW",s.get("activeCategory").asText());assertTrue(s.get("routePending").asBoolean());
        call(post("/api/need-sessions/"+id+"/confirm"),Map.of("revision",rev(s)),owner,409);
        s=call(post("/api/need-sessions/"+id+"/intake"),Map.of("revision",rev(s),"text","帮我弄那个"),owner,200);
        assertEquals(0,s.get("candidates").size());
    }
    @Test void missingConflictingAndForeignFieldsCannotBeConfirmed() throws Exception {
        var s=select(create(),"RUNNING");var id=s.get("id").asLong();
        call(put("/api/need-sessions/"+id+"/draft"),Map.of("revision",rev(s),"fields",Map.of("returnAt","bad"),"contexts",List.of()),owner,400);
        call(post("/api/need-sessions/"+id+"/confirm"),Map.of("revision",rev(s)),owner,422);
        call(put("/api/need-sessions/"+id+"/draft"),Map.of("revision",-1,"fields",Map.of(),"contexts",List.of()),owner,400);
    }
    @Test void simultaneousSameRevisionEditsHaveOneWinner() throws Exception {
        var s=select(create(),"STUDY");long id=s.get("id").asLong(),version=rev(s);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var start=new CountDownLatch(1);var done=new ArrayList<Future<Integer>>();
            for(String text:List.of("第一个问题","另一个问题")) done.add(pool.submit(()->{
                start.await();return mvc.perform(put("/api/need-sessions/"+id+"/draft").header("Authorization","Bearer "+owner).contentType("application/json").content(json.writeValueAsString(Map.of("revision",version,"fields",Map.of("context",text),"contexts",List.of())))).andReturn().getResponse().getStatus();
            }));
            start.countDown();var results=new ArrayList<Integer>();for(var f:done)results.add(f.get(15,TimeUnit.SECONDS));Collections.sort(results);
            assertEquals(List.of(200,409),results);
        }
    }
    @Test void deleteOnlyOwnedDraftData() throws Exception {
        var s=select(create(),"STUDY");var id=s.get("id").asLong();
        call(delete("/api/need-sessions/"+id),Map.of("revision",rev(s)),other,404);
        call(delete("/api/need-sessions/"+id),Map.of("revision",rev(s)),owner,200);
        call(get("/api/need-sessions/"+id),null,owner,404);assertEquals(0,drafts.count());assertEquals(0,messages.count());
    }
}
