package com.hyeja.recommendationservice.profile.dto;

import com.hyeja.recommendationservice.profile.enums.EmploymentStatus;
import com.hyeja.recommendationservice.profile.enums.IncomeRange;
import com.hyeja.recommendationservice.region.dto.RegionDTO;
import java.time.LocalDate;
import lombok.Builder;
import lombok.Getter;

// 자격 판정에 쓰는 회원 조건만 담습니다. 1차 Profile 엔티티의 같은 이름 필드에 대응하며, 값이 없으면 "미입력"으로 판정합니다.
@Getter
@Builder
public class EligibilityProfileDTO {
    private final LocalDate birth;
    private final RegionDTO region;
    private final EmploymentStatus employmentCode;
    private final Boolean houselessYn;
    private final IncomeRange incomeRangeCode;
}
