package com.example.campushelp.transition;

import com.example.campushelp.entity.*;
import com.example.campushelp.repository.*;
import com.example.campushelp.util.JwtUtil;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static com.example.campushelp.transition.TaskPreviewContracts.*;

@SpringBootTest @ActiveProfiles("test") @AutoConfigureMockMvc
class TaskPreviewIntegrationTest {
    @Autowired MockMvc mvc;@Autowired JwtUtil jwt;@Autowired UserRepository users;@Autowired DemandRepository demands;
    @MockBean CangjieTaskPreview component;
    Long publisher,helper;Demand task;
    User user() {var u=new User();u.setUsername("preview-"+UUID.randomUUID());u.setPassword("fixture");return users.saveAndFlush(u);}
    @BeforeEach void setup() {
        publisher=user().getId();helper=user().getId();task=new Demand();task.setTitle("preview fixture");task.setDescription("immutable snapshot");task.setPublisherId(publisher);
        task.setCategory("BORROW");task.setStatus("PENDING");task.setLocation("fixture");task.setReward(BigDecimal.ONE);task.setExpectedTime(LocalDateTime.now().plusDays(1));task=demands.saveAndFlush(task);
        when(component.project(any(),any(),any())).thenReturn(new Decision("APPLIED","IN_PROGRESS",1));
    }
    @Test void derivesRoleAndVersionFromDatabaseAndNeverExecutesBusinessEffects() throws Exception {
        long version=task.getRowVersion(),count=demands.count();
        var response=mvc.perform(post("/api/task-transition-preview").header("Authorization","Bearer "+jwt.generateToken(helper))
            .contentType("application/json").content("{\"demandId\":"+task.getId()+",\"event\":\"ACCEPT\"}")).andReturn().getResponse();
        assertEquals(200,response.getStatus());assertTrue(response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8).contains("尚未执行"));
        verify(component).project(new Snapshot(task.getId(),"BORROW","PENDING",version),Event.ACCEPT,Participant.OTHER);
        var unchanged=demands.findById(task.getId()).orElseThrow();assertEquals("PENDING",unchanged.getStatus());assertNull(unchanged.getAcceptorId());assertEquals(version,unchanged.getRowVersion());assertEquals(count,demands.count());
    }
    @Test void nonParticipantCannotInspectAnAcceptedTask() throws Exception {
        task.setStatus("IN_PROGRESS");task.setAcceptorId(helper);demands.saveAndFlush(task);Long outsider=user().getId();
        var response=mvc.perform(post("/api/task-transition-preview").header("Authorization","Bearer "+jwt.generateToken(outsider))
            .contentType("application/json").content("{\"demandId\":"+task.getId()+",\"event\":\"CANCEL\"}")).andReturn().getResponse();
        assertEquals(404,response.getStatus());verifyNoInteractions(component);
    }
    @Test void requiresAuthenticationAndRejectsUnknownEvents() throws Exception {
        assertEquals(403,mvc.perform(post("/api/task-transition-preview").contentType("application/json").content("{}")).andReturn().getResponse().getStatus());
        assertEquals(400,mvc.perform(post("/api/task-transition-preview").header("Authorization","Bearer "+jwt.generateToken(helper)).contentType("application/json").content("{\"demandId\":1,\"event\":\"PUBLISH\"}")).andReturn().getResponse().getStatus());
        verifyNoInteractions(component);
    }
}
