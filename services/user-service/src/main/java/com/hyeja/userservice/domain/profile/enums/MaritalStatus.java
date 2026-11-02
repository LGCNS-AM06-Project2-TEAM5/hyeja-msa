package com.hyeja.userservice.domain.profile.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MaritalStatus {

    SINGLE("미혼"),
    MARRIED("기혼");

    private final String label;
}
