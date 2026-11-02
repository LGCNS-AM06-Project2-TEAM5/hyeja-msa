package com.hyeja.userservice.domain.profile.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum HousingType {

    PARENTS("부모님 집"),
    MONTHLY_RENT("월세"),
    JEONSE("전세"),
    OWNED("자가");

    private final String label;
}
