package com.example.campushelp.service;

import com.example.campushelp.dto.request.AppealRequest;
import com.example.campushelp.dto.response.AppealResponse;
import java.util.List;

public interface AppealService {
    AppealResponse createAppeal(Long userId, AppealRequest request);
    List<AppealResponse> getMyAppeals(Long userId);
    List<AppealResponse> getAllAppeals();
    List<AppealResponse> getPendingAppeals();
    AppealResponse handleAppeal(Long appealId, Long adminId, String action, String adminNote);
}
