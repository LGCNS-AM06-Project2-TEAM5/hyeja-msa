
package com.hyeja.engagementservice.favorite.controller;

import com.hyeja.engagementservice.favorite.dto.FavoriteInternalRequestDTO.ActivePolicyIdsRequest;
import com.hyeja.engagementservice.favorite.service.FavoriteService;
import com.hyeja.engagementservice.global.apiPayload.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/favorites")
public class InternalFavoriteController {

    private final FavoriteService favoriteService;

    @PostMapping("/active-policy-ids")
    public ApiResponse<List<String>> findActivePolicyIds(
            @Valid @RequestBody ActivePolicyIdsRequest request
    ) {
        List<String> result = favoriteService.findActivePolicyIds(
                request.memberId(),
                request.policyIds()
        );

        return ApiResponse.onSuccess(result);
    }
}
