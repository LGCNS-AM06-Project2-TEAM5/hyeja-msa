package com.hyeja.userservice.domain.region.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

// 지역 코드와 시군구 이름. recommendation의 RegionDTO, policy F2의 regions 항목과 같은 형태입니다.
@Getter
@AllArgsConstructor
public class RegionDTO {

    private final String regionCode;
    private final String sigunguName;
}
