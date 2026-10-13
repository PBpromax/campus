package com.example.campushelp.service;

import com.example.campushelp.dto.request.DemandRequest;
import com.example.campushelp.dto.response.DemandResponse;

import java.util.List;

public interface DemandService {
    DemandResponse createDemand(Long userId, DemandRequest request);
    DemandResponse getDemandById(Long id);
    List<DemandResponse> getAllDemands(String category, String status, String keyword);
    List<DemandResponse> getDemandsByStatus(String status);
    List<DemandResponse> getDemandsByCategory(String category);
    List<DemandResponse> getDemandsByPublisher(Long publisherId);
    List<DemandResponse> getDemandsByAcceptor(Long acceptorId);
    DemandResponse acceptDemand(Long demandId, Long userId);
    DemandResponse completeDemand(Long demandId, Long userId, String proof);
    DemandResponse confirmDemand(Long demandId, Long userId);
    DemandResponse cancelDemand(Long demandId, Long userId, String reason);
    void deleteDemand(Long demandId, Long userId);
}
