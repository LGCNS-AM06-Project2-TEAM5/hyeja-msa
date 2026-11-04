package com.hyeja.userservice.domain.profile.dto;

import com.hyeja.userservice.domain.profile.enums.EducationLevel;
import com.hyeja.userservice.domain.profile.enums.EmploymentStatus;
import com.hyeja.userservice.domain.profile.enums.HousingType;
import com.hyeja.userservice.domain.profile.enums.IncomeRange;
import com.hyeja.userservice.domain.profile.enums.MaritalStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 내 조건 입력값입니다. 회원가입 요청(MemberSignupRequestDTO)의 profile 필드와 내 조건 수정에서 함께 씁니다.
// 선택 항목(혼인·소득·학력·주거 형태)은 비워서(null) 보낼 수 있습니다.
@Builder
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class ProfileRequestDTO {

    @NotNull(message = "생년월일은 필수입니다.")
    @Past(message = "생년월일은 오늘 이전이어야 합니다.")
    private LocalDate birth;

    // 시군구 코드 5자리. region 테이블에 있는지는 ProfileService가 확인합니다.
    @NotBlank(message = "거주지는 필수입니다.")
    private String regionCode;

    @NotNull(message = "취업 상태는 필수입니다.")
    private EmploymentStatus employmentCode;

    @NotNull(message = "무주택 여부는 필수입니다.")
    private Boolean houselessYn;

    private MaritalStatus marriageCode;

    private IncomeRange incomeRangeCode;

    private EducationLevel educationCode;

    private HousingType housingType;
}
