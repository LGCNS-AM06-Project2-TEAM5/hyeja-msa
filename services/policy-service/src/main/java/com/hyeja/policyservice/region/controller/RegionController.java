package com.hyeja.policyservice.region.controller;

import com.hyeja.policyservice.global.apiPayload.ApiResponse;
import com.hyeja.policyservice.region.dto.RegionResponseDTO.SidoDTO;
import com.hyeja.policyservice.region.service.RegionService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 거주지 선택용 지역 API
@RestController
@RequestMapping("/api/regions")
@RequiredArgsConstructor
public class RegionController {

    private final RegionService regionService;

    // 시군구 목록 (회원가입 S-04·내 조건 수정 S-08의 거주지 2단 드롭다운) — 예: GET /api/regions
    // 전체 시·군·구를 시·도별로 묶어 코드 순으로 한 번에 반환합니다. 비로그인도 호출할 수 있습니다(가입 전 화면에서 씀).
    @GetMapping("")
    public ApiResponse<List<SidoDTO>> getRegions() {
        return ApiResponse.onSuccess(regionService.getRegions());
    }
}
