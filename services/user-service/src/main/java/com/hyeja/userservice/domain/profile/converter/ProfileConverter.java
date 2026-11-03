package com.hyeja.userservice.domain.profile.converter;

import com.hyeja.userservice.domain.profile.dto.ProfileResponseDTO;
import com.hyeja.userservice.domain.profile.entity.Profile;
import java.time.LocalDate;
import java.time.Period;
import java.util.function.Function;

public final class ProfileConverter {

    private ProfileConverter() {
    }

    // 내 조건 조회·수정 응답으로 변환합니다.
    // regionName은 user_db 지역 복사본에서 찾은 이름(없으면 null), today는 만 나이 계산 기준일(한국 날짜)입니다.
    public static ProfileResponseDTO toProfileResponseDTO(Profile profile, String regionName, LocalDate today) {
        return ProfileResponseDTO.builder()
                .birth(profile.getBirth())
                .age(Period.between(profile.getBirth(), today).getYears())
                .regionCode(profile.getRegionCode())
                .regionName(regionName)
                .employmentCode(profile.getEmploymentCode())
                .employmentName(profile.getEmploymentCode().getLabel())
                .houselessYn(profile.getHouselessYn())
                // 아래는 선택 항목이라 등록하지 않았으면 이름도 null로 둡니다.
                .marriageCode(profile.getMarriageCode())
                .marriageName(label(profile.getMarriageCode(), m -> m.getLabel()))
                .incomeRangeCode(profile.getIncomeRangeCode())
                .incomeRangeName(label(profile.getIncomeRangeCode(), i -> i.getLabel()))
                .educationCode(profile.getEducationCode())
                .educationName(label(profile.getEducationCode(), e -> e.getLabel()))
                .housingType(profile.getHousingType())
                .housingTypeName(label(profile.getHousingType(), h -> h.getLabel()))
                .updatedAt(profile.getUpdatedAt())
                .build();
    }

    private static <T> String label(T code, Function<T, String> getLabel) {
        return code == null ? null : getLabel.apply(code);
    }
}
