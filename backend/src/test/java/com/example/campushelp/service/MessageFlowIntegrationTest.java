package com.example.campushelp.service;

import com.example.campushelp.dto.request.*;
import com.example.campushelp.entity.User;
import com.example.campushelp.exception.BusinessException;
import com.example.campushelp.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest @ActiveProfiles("test") @Transactional
class MessageFlowIntegrationTest {
    @Autowired UserRepository users;
    @Autowired DemandService demands;
    @Autowired MessageService messages;
    @Test void missingDisplayNameStillAllowsPrivateTwoParticipantConversation() {
        var a=new User();a.setUsername("message_fixture_a");a.setPassword("fixture-hash");a.setCreditScore(100);users.saveAndFlush(a);
        var b=new User();b.setUsername("message_fixture_b");b.setPassword("fixture-hash");b.setCreditScore(100);users.saveAndFlush(b);
        var request=new DemandRequest();request.setTitle("fixture");request.setDescription("fixture");request.setCategory("STUDY");
        request.setReward(BigDecimal.ONE);request.setLocation("线上");request.setExpectedTime(LocalDateTime.now().plusDays(1));
        var task=demands.createDemand(a.getId(),request);demands.acceptDemand(task.getId(),b.getId());
        var send=new MessageRequest();send.setDemandId(task.getId());send.setReceiverId(a.getId());send.setContent("请确认问题上下文");
        var result=messages.sendMessage(b.getId(),send);
        assertEquals("message_fixture_b",result.getSenderName());assertNotNull(result.getId());
        assertEquals(1,messages.getMessagesByDemand(task.getId(),a.getId()).size());
        send.setReceiverId(999999L);
        assertThrows(BusinessException.class,()->messages.sendMessage(b.getId(),send));
        assertEquals(1,messages.getMessagesByDemand(task.getId(),a.getId()).size());
        assertThrows(BusinessException.class,()->messages.getMessagesByDemand(task.getId(),999999L));
    }
}
