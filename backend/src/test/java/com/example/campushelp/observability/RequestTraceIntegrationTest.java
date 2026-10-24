package com.example.campushelp.observability;

import com.example.campushelp.ai.*;
import com.example.campushelp.domain.*;
import com.example.campushelp.entity.User;
import com.example.campushelp.repository.UserRepository;
import com.example.campushelp.tools.*;
import com.example.campushelp.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static com.example.campushelp.domain.NeedContracts.*;

@SpringBootTest @ActiveProfiles("test") @AutoConfigureMockMvc(print=MockMvcPrint.NONE)
class RequestTraceIntegrationTest {
    @Autowired MockMvc mvc;@Autowired JwtUtil jwt;@Autowired ObjectMapper json;
    @Autowired UserRepository users;@Autowired NeedService needs;@Autowired ToolAuditRepository audits;
    @Autowired ModelObservations observations;
    @MockBean DeepSeekClient client;
    User user(String role) {var user=new User();user.setUsername("trace-"+UUID.randomUUID());user.setPassword("fixture-hash");user.setRole(role);return users.saveAndFlush(user);}
    @Test void createsIdsBeforeAuthenticationAndIgnoresInvalidCorrelationInput() throws Exception {
        var response=mvc.perform(get("/api/trusted-tools").header(RequestTrace.TRACE_HEADER,"invalid\nprivate")).andReturn().getResponse();
        assertEquals(403,response.getStatus());UUID.fromString(response.getHeader(RequestTrace.TRACE_HEADER));UUID.fromString(response.getHeader(RequestTrace.REQUEST_HEADER));
        assertNull(RequestTrace.traceId());assertNull(RequestTrace.requestId());
    }
    @Test void callerCanGroupRequestsButCannotChooseTheirRequestIdOrObtainAdminMetrics() throws Exception {
        var u=user("USER");String trace=UUID.randomUUID().toString();
        var request=get("/api/ops/model-observations").header("Authorization","Bearer "+jwt.generateToken(u.getId())).header(RequestTrace.TRACE_HEADER,trace).header(RequestTrace.REQUEST_HEADER,trace);
        var response=mvc.perform(request).andReturn().getResponse();
        assertEquals(403,response.getStatus());assertEquals(trace,response.getHeader(RequestTrace.TRACE_HEADER));assertNotEquals(trace,response.getHeader(RequestTrace.REQUEST_HEADER));
        assertFalse(response.getContentAsString().contains("recent"));
        var admin=user("ADMIN");var success=mvc.perform(get("/api/ops/model-observations").header("Authorization","Bearer "+jwt.generateToken(admin.getId()))).andReturn().getResponse();
        assertEquals(200,success.getStatus());assertEquals("PROCESS_LIFETIME_RESETS_ON_RESTART",json.readTree(success.getContentAsByteArray()).at("/data/window").asText());
    }
    @Test void workerCarriesRequestTraceAndRejectsStaleRequestsWithoutLeakingAdmission() throws Exception {
        var u=user("USER");var s=needs.create(u.getId());s=needs.select(u.getId(),s.id(),new Select(s.revision(),NeedCategory.STUDY));
        when(client.configured()).thenReturn(true);when(client.model()).thenReturn("fixture");
        String trace=UUID.randomUUID().toString();var seen=new AtomicReference<String>();var span=new AtomicReference<String>();
        doAnswer(inv->{seen.set(RequestTrace.traceId());span.set(RequestTrace.requestId());return new DeepSeekClient.Reply("fixture answer","");})
            .when(client).stream(anyList(),anyMap(),any(),any());
        String url="/api/need-sessions/"+s.id()+"/ai-stream",auth="Bearer "+jwt.generateToken(u.getId());
        var stale=mvc.perform(post(url).header("Authorization",auth).accept("text/event-stream").contentType("application/json").content("{\"revision\":0,\"text\":\"stale\"}")).andReturn();
        assertEquals(409,stale.getResponse().getStatus());
        var started=mvc.perform(post(url).header("Authorization",auth).header(RequestTrace.TRACE_HEADER,trace).contentType("application/json").content(json.writeValueAsString(new Message(s.revision(),"fixture")))).andReturn();
        started.getAsyncResult(5000);var response=mvc.perform(asyncDispatch(started)).andReturn().getResponse();
        assertTrue(response.getContentAsString().contains("event:done"));assertEquals(trace,seen.get());assertEquals(response.getHeader(RequestTrace.REQUEST_HEADER),span.get());
        assertTrue(observations.snapshot().recent().stream().anyMatch(c->trace.equals(c.traceId())));assertNull(RequestTrace.traceId());
    }
    @Test void rejectedToolConfirmationPersistsTheSameTraceAndRequestId() throws Exception {
        var u=user("USER");String trace=UUID.randomUUID().toString(),intent=UUID.randomUUID().toString();
        var response=mvc.perform(post("/api/trusted-tools/"+intent+"/execute").header("Authorization","Bearer "+jwt.generateToken(u.getId()))
            .header(RequestTrace.TRACE_HEADER,trace).contentType("application/json").content("{\"confirmed\":false}")).andReturn().getResponse();
        assertEquals(422,response.getStatus());
        var audit=audits.findAll().stream().filter(a->intent.equals(a.getIntentId())).findFirst().orElseThrow();
        assertEquals(trace,audit.getTraceId());assertEquals(response.getHeader(RequestTrace.REQUEST_HEADER),audit.getRequestId());
        assertEquals("REJECTED",audit.getOutcome());
    }
}
