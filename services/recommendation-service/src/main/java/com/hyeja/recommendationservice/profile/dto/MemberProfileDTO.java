package com.hyeja.recommendationservice.profile.dto;

import com.hyeja.recommendationservice.profile.enums.EducationLevel;
import com.hyeja.recommendationservice.profile.enums.EmploymentStatus;
import com.hyeja.recommendationservice.profile.enums.HousingType;
import com.hyeja.recommendationservice.profile.enums.IncomeRange;
import com.hyeja.recommendationservice.profile.enums.MaritalStatus;
import java.time.LocalDate;

// F1 응답(GET /internal/members/{memberId}/profile)의 회원 조건입니다. ProfileClientAdapter가 SearchProfileDTO로 바꿉니다.
// marriageCode·incomeRangeCode·educationCode·housingType과 region.sigunguName은 계약상 null일 수 있습니다.
public record MemberProfileDTO(
        Long memberId,
        LocalDate birth,
        Region region,
        EmploymentStatus employmentCode,
        Boolean houselessYn,
        MaritalStatus marriageCode,
        IncomeRange incomeRangeCode,
        EducationLevel educationCode,
        HousingType housingType) {

    public record Region(String regionCode, String sigunguName) {
    }
}
