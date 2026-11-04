package com.hyeja.userservice.domain.profile.dto;

import com.hyeja.userservice.domain.profile.enums.EducationLevel;
import com.hyeja.userservice.domain.profile.enums.EmploymentStatus;
import com.hyeja.userservice.domain.profile.enums.HousingType;
import com.hyeja.userservice.domain.profile.enums.IncomeRange;
import com.hyeja.userservice.domain.profile.enums.MaritalStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

// 마이페이지 내 조건 탭·추천 화면에서 쓰는 내 조건 응답입니다. 1차와 같은 형식입니다.
// 코드(enum 이름)와 한글 이름을 같이 내려줍니다. 화면은 이름을 보여주고, 수정 폼은 코드로 선택 상태를 맞춥니다.
// 선택 항목(혼인·소득·학력·주거 형태)을 등록하지 않았으면 코드와 이름 모두 null입니다.
@Getter
@Builder
public class ProfileResponseDTO {

    private final LocalDate birth;
    // 만 나이 (저장하지 않고 birth로 매번 계산)
    private final Integer age;
    // 시군구코드 5자리와 지역명 (예: 11440 / 서울특별시 마포구). user_db 지역 복사본에 없으면 regionName은 null
    private final String regionCode;
    private final String regionName;
    private final EmploymentStatus employmentCode;
    private final String employmentName;
    private final Boolean houselessYn;
    private final MaritalStatus marriageCode;
    private final String marriageName;
    private final IncomeRange incomeRangeCode;
    private final String incomeRangeName;
    private final EducationLevel educationCode;
    private final String educationName;
    private final HousingType housingType;
    private final String housingTypeName;
    // 조건 마지막 수정 시각 (BaseEntity의 updated_at)
    private final LocalDateTime updatedAt;
}
