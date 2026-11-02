package com.hyeja.policyservice.region.dto;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

// 거주지 2단 드롭다운(시·도 → 시·군·구)용 지역 목록 응답입니다.
public final class RegionResponseDTO {

    private RegionResponseDTO() {
    }

    // 시·도와 그 안의 시·군·구 목록
    @Getter
    @Builder
    public static class SidoDTO {

        // 시·도 코드 (시군구코드 앞 2자리), 예: "11"
        private String sidoCode;

        // 시·도 이름, 예: "서울특별시"
        private String sidoName;

        // 시·군·구 목록 (코드 순)
        private List<SigunguDTO> sigungu;
    }

    @Getter
    @Builder
    public static class SigunguDTO {

        // 시군구코드 5자리 (회원가입·조건 수정의 regionCode로 보냄), 예: "11440"
        private String regionCode;

        // 시·군·구 이름 (시·도 이름 제외, 세종은 세종특별자치시), 예: "마포구"
        private String sigunguName;
    }
}
