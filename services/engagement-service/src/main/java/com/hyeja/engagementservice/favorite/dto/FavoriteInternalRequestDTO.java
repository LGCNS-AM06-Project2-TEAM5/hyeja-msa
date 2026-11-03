
package com.hyeja.engagementservice.favorite.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public class FavoriteInternalRequestDTO {

    public record ActivePolicyIdsRequest(
            @NotNull
            @Positive
            Long memberId,

            @NotNull
            @Size(max = 20)
            List<@NotEmpty String> policyIds
    ) {
    }
}

