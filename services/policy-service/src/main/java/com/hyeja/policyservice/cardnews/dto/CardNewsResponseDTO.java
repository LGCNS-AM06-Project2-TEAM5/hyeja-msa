package com.hyeja.policyservice.cardnews.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Builder
@Getter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class CardNewsResponseDTO {

    private String policyId;

    private String policyName;

    private String description;

    private String applyEndDate;
}
