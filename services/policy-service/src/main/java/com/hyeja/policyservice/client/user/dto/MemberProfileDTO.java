package com.hyeja.policyservice.client.user.dto;

import com.hyeja.policyservice.client.user.enums.EmploymentStatus;
import com.hyeja.policyservice.client.user.enums.IncomeRange;
import java.time.LocalDate;

// F6 회원 조건 응답(user-service MemberProfileDTO) 중 policy가 쓰는 값만 받습니다.
// 결혼·학력·주거 형태는 policy 판정에 쓰지 않아 받지 않습니다. 선택 항목(incomeRangeCode)은 null일 수 있습니다.
public record MemberProfileDTO(
        Long memberId,
        LocalDate birth,
        RegionDTO region,
        EmploymentStatus employmentCode,
        Boolean houselessYn,
        IncomeRange incomeRangeCode) {

    // sigunguName은 user 쪽 지역 복사본에 코드가 없으면 null입니다. regionCode는 항상 있습니다.
    public record RegionDTO(String regionCode, String sigunguName) {
    }
}
