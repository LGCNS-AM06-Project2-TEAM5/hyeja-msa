package com.hyeja.policyservice.internal.controller;

import com.hyeja.policyservice.global.apiPayload.ApiResponse;
import com.hyeja.policyservice.internal.dto.PolicyCandidateDTO;
import com.hyeja.policyservice.internal.dto.PolicyCandidateRequest;
import com.hyeja.policyservice.internal.service.PolicyCandidateService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/policies")
public class PolicyCandidateController {
    private final PolicyCandidateService policyCandidateService;

    @PostMapping("/candidates")
    public ApiResponse<List<PolicyCandidateDTO>> findCandidates(
            @Valid @RequestBody PolicyCandidateRequest request) {
        return ApiResponse.onSuccess(policyCandidateService.findCandidates(request));
    }
}
