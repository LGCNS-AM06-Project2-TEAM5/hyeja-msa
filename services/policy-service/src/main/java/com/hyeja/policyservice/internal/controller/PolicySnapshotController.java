package com.hyeja.policyservice.internal.controller;

import com.hyeja.policyservice.global.apiPayload.ApiResponse;
import com.hyeja.policyservice.internal.dto.PolicySnapshotDTO;
import com.hyeja.policyservice.internal.service.PolicySnapshotService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/policies")
public class PolicySnapshotController {
    private final PolicySnapshotService policySnapshotService;

    @GetMapping("/{policyId}")
    public ApiResponse<PolicySnapshotDTO> getSnapshot(@PathVariable String policyId) {
        return ApiResponse.onSuccess(policySnapshotService.getSnapshot(policyId));
    }
}
