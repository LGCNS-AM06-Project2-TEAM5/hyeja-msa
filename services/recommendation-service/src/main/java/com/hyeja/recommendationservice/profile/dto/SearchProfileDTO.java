package com.hyeja.recommendationservice.profile.dto;

import com.hyeja.recommendationservice.profile.enums.EducationLevel;
import com.hyeja.recommendationservice.profile.enums.HousingType;
import com.hyeja.recommendationservice.profile.enums.MaritalStatus;

// 검색에 쓰는 회원 조건. 자격 판정 값(eligibility)과, 판정에는 쓰지 않고 AI 추가 자격 검토에만 넘기는 값입니다.
public record SearchProfileDTO(
        EligibilityProfileDTO eligibility,
        MaritalStatus marriageCode,
        EducationLevel educationCode,
        HousingType housingType) {
}
