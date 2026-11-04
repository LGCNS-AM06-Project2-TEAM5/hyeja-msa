package com.hyeja.policyservice.client.user.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// user-service Profile enum과 같은 이름입니다(F6 응답을 그대로 받음).
@Getter
@RequiredArgsConstructor
public enum IncomeRange {
    UNDER_2000("2천만원 미만"),
    R2000_3000("2천만원 이상 3천만원 미만"),
    R3000_4000("3천만원 이상 4천만원 미만"),
    R4000_5000("4천만원 이상 5천만원 미만"),
    OVER_5000("5천만원 이상");

    private final String label;
}
