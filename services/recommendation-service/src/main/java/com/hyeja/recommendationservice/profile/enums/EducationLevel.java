package com.hyeja.recommendationservice.profile.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum EducationLevel {
    BELOW_HIGH_SCHOOL("고졸 미만"),
    HIGH_SCHOOL_STUDENT("고교 재학"),
    HIGH_SCHOOL_EXPECTED_GRADUATE("고졸 예정"),
    HIGH_SCHOOL_GRADUATE("고교 졸업"),
    COLLEGE_GRADUATE("대학 졸업"),
    COLLEGE_EXPECTED_GRADUATE("대졸 예정"),
    COLLEGE_STUDENT("대학 재학"),
    MASTER_OR_DOCTOR("석박사"),
    OTHER("기타");

    private final String label;
}
