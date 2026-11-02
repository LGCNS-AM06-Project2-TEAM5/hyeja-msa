package com.hyeja.userservice.domain.profile.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum EmploymentStatus {

    EMPLOYED("재직자"),
    SELF_EMPLOYED("자영업자"),
    UNEMPLOYED("미취업자"),
    FREELANCER("프리랜서"),
    DAILY_WORKER("일용근로자"),
    ENTREPRENEUR("(예비)창업자"),
    SHORT_TERM_WORKER("단기근로자"),
    FARMER("영농종사자"),
    OTHER("기타");

    private final String label;
}
