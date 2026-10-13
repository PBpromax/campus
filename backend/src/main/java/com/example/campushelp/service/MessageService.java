package com.example.campushelp.service;

import com.example.campushelp.dto.request.MessageRequest;
import com.example.campushelp.dto.response.MessageResponse;
import java.util.List;

public interface MessageService {
    MessageResponse sendMessage(Long senderId, MessageRequest request);
    List<MessageResponse> getMessagesByDemand(Long demandId, Long userId);
    List<MessageResponse> getMyConversations(Long userId);
}
