package com.example.campushelp.ai;

import com.example.campushelp.domain.*;
import com.example.campushelp.util.JwtUtil;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import java.util.*;
import java.util.function.Consumer;
import static com.example.campushelp.domain.NeedContracts.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class AiIntegrationTest {
    @Autowired NeedService needs;
    @Autowired AiProposalService proposals;
    @Autowired NeedMessageRepository messages;
    @Autowired NeedDraftRepository drafts;
    @Autowired NeedSessionRepository sessions;
    @Autowired ObjectMapper json;
    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwt;
    @MockBean DeepSeekClient client;
    @BeforeEach void setup() {
        messages.deleteAll();drafts.deleteAll();sessions.deleteAll();when(client.configured()).thenReturn(true);
    }
    @ParameterizedTest @EnumSource(NeedCategory.class)
    void streamsPersistsAndOnlyAdoptsFieldsAfterUserAction(NeedCategory category) throws Exception {
        var s=needs.create(8101L);s=needs.select(8101L,s.id(),new Select(s.revision(),category));
        String field=category==NeedCategory.STUDY?"context":category==NeedCategory.OTHER?"serviceGoal":"item";
        String args=json.writeValueAsString(Map.of("fields",Map.of(field,"测试内容"),"candidates",List.of()));
        doAnswer(inv->{Consumer<String> emit=inv.getArgument(2);emit.accept("请核对建议");return new DeepSeekClient.Reply("请核对建议",args);})
            .when(client).stream(anyList(),anyMap(),any(),any());
        var result=mvc.perform(post("/api/need-sessions/"+s.id()+"/ai-stream").header("Authorization","Bearer "+jwt.generateToken(8101L))
            .contentType("application/json").content(json.writeValueAsString(new Message(s.revision(),"测试输入")))).andReturn();
        result.getAsyncResult(5000);
        var finalResult=mvc.perform(asyncDispatch(result)).andReturn();
        assertEquals(200,finalResult.getResponse().getStatus());String stream=finalResult.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(stream.contains("event:done"),stream);assertTrue(stream.contains("event:text_delta"));
        var remote=needs.get(8101L,s.id());assertEquals("ASSISTANT",remote.messages().getLast().role());
        assertTrue(remote.drafts().getFirst().fields().isEmpty());
        int start=stream.indexOf("event:proposal\n");assertTrue(start>=0,stream);
        String line=stream.substring(start).split("\n")[1].substring(5);
        var proposal=json.readTree(line);
        var adopted=proposals.apply(8101L,s.id(),proposal.get("token").asText(),new Version(remote.revision()));
        assertEquals("测试内容",adopted.drafts().getFirst().fields().get(field));assertNull(adopted.drafts().getFirst().confirmedRevision());
        assertThrows(RuntimeException.class,()->proposals.apply(8101L,adopted.id(),proposal.get("token").asText(),new Version(adopted.revision())));
    }
    @Test void proposalsRejectForeignFieldsAndAuthorization() throws Exception {
        var s=needs.create(8101L);s=needs.select(8101L,s.id(),new Select(s.revision(),NeedCategory.STUDY));final var selected=s;
        for(String field:List.of("item","consent","deliveryConsent")) {
            String args=json.writeValueAsString(Map.of("fields",Map.of(field,"任意值"),"candidates",List.of()));
            assertThrows(Exception.class,()->proposals.offer(8101L,selected,args));
        }
    }
    @Test void staleProposalAndOtherUserCannotApply() throws Exception {
        var s=needs.create(8101L);s=needs.select(8101L,s.id(),new Select(s.revision(),NeedCategory.STUDY));
        var p=proposals.offer(8101L,s,"{\"fields\":{\"context\":\"x\"},\"candidates\":[]}");
        assertThrows(Exception.class,()->proposals.apply(8102L,p.sessionId(),p.token(),new Version(p.revision())));
        needs.message(8101L,s.id(),new Message(s.revision(),"纠正问题"));
        assertThrows(Exception.class,()->proposals.apply(8101L,p.sessionId(),p.token(),new Version(p.revision())));
    }
    @Test void unselectedDoesNotAcceptFieldsOrAutoOther() throws Exception {
        var s=needs.create(8101L);
        assertThrows(Exception.class,()->proposals.offer(8101L,s,"{\"fields\":{\"item\":\"x\"},\"candidates\":[]}"));
        var p=proposals.offer(8101L,s,"{\"fields\":{},\"candidates\":[\"BORROW\",\"RUNNING\"]}");
        assertNull(needs.get(8101L,s.id()).activeCategory());assertEquals(2,p.candidates().size());
    }
    @Test void itemCountsCannotBecomeHelperCounts() throws Exception {
        var s=needs.create(8101L);s=needs.select(8101L,s.id(),new Select(s.revision(),NeedCategory.OTHER));
        s=needs.message(8101L,s.id(),new Message(s.revision(),"摆放两张桌子和三张海报"));
        var p=proposals.offer(8101L,s,"{\"fields\":{\"helperCount\":\"2\",\"serviceGoal\":\"活动布置\"},\"candidates\":[]}");
        assertFalse(p.fields().containsKey("helperCount"));assertFalse(p.warnings().isEmpty());assertTrue(p.fields().containsKey("serviceGoal"));
    }
    @Test void explicitlyRequestedPeopleCountCanBeProposed() throws Exception {
        var s=needs.create(8101L);s=needs.select(8101L,s.id(),new Select(s.revision(),NeedCategory.OTHER));
        s=needs.message(8101L,s.id(),new Message(s.revision(),"需要两位同学帮忙布置"));
        var p=proposals.offer(8101L,s,"{\"fields\":{\"helperCount\":\"2\"},\"candidates\":[]}");
        assertEquals("2",p.fields().get("helperCount"));assertTrue(p.warnings().isEmpty());
    }
    @Test void authenticationAndMissingConfigDoNotWriteMessages() throws Exception {
        var s=needs.create(8101L);
        assertEquals(403,mvc.perform(post("/api/need-sessions/"+s.id()+"/ai-stream").contentType("application/json").content("{\"revision\":0,\"text\":\"x\"}")).andReturn().getResponse().getStatus());
        when(client.configured()).thenReturn(false);
        var result=mvc.perform(post("/api/need-sessions/"+s.id()+"/ai-stream").header("Authorization","Bearer "+jwt.generateToken(8101L))
            .contentType("application/json").content("{\"revision\":0,\"text\":\"x\"}")).andReturn();
        assertEquals(503,result.getResponse().getStatus());assertEquals(0,needs.get(8101L,s.id()).messages().size());
    }
}
