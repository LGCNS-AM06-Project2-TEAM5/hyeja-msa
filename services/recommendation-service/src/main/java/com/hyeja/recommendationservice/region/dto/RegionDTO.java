package com.hyeja.recommendationservice.region.dto;

import lombok.Builder;
import lombok.Getter;

// 회원 거주지와 정책 대상 지역에 함께 씁니다. 1차 Region 엔티티의 코드(시군구 5자리)와 이름에 대응합니다.
@Getter
@Builder
public class RegionDTO {
    private final String regionCode;
    private final String sigunguName;
}
