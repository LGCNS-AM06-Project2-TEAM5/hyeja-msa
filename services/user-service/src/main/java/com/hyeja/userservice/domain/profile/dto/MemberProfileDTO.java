package com.hyeja.userservice.domain.profile.dto;

import com.hyeja.userservice.domain.profile.enums.EducationLevel;
import com.hyeja.userservice.domain.profile.enums.EmploymentStatus;
import com.hyeja.userservice.domain.profile.enums.HousingType;
import com.hyeja.userservice.domain.profile.enums.IncomeRange;
import com.hyeja.userservice.domain.profile.enums.MaritalStatus;
import com.hyeja.userservice.domain.region.dto.RegionDTO;
import java.time.LocalDate;
import lombok.Builder;
import lombok.Getter;

// 내부 API(F1·F6) 응답. recommendation·policy가 자격 판정과 정책 필터에 씁니다.
// 나이는 계산하지 않고 birth만 주며, 판정 기준 날짜는 호출하는 쪽이 정합니다.
@Getter
@Builder
public class MemberProfileDTO {

    private final Long memberId;
    private final LocalDate birth;
    private final RegionDTO region;
    private final EmploymentStatus employmentCode;
    private final Boolean houselessYn;
    private final MaritalStatus marriageCode;
    private final IncomeRange incomeRangeCode;
    private final EducationLevel educationCode;
    private final HousingType housingType;
}
