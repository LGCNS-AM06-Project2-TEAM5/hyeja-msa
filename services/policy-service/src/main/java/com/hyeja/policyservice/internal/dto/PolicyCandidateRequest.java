package com.hyeja.policyservice.internal.dto;

import com.hyeja.policyservice.policy.enums.PolicyCategory;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.util.List;

public record PolicyCandidateRequest(
        @NotNull List<@NotNull PolicyCategory> categories,
        @NotNull @Pattern(regexp = "[0-9]{5}") String regionCode,
        @NotNull LocalDate today) {
}
