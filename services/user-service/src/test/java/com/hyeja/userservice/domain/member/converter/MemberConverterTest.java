package com.hyeja.userservice.domain.member.converter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MemberConverterTest {

    @Test
    void masksEmailLocalPart() {
        assertThat(MemberConverter.maskEmail("mingy@hyeja.kr")).isEqualTo("min***@hyeja.kr");
        assertThat(MemberConverter.maskEmail("abc@hyeja.kr")).isEqualTo("a***@hyeja.kr");
        assertThat(MemberConverter.maskEmail("a@hyeja.kr")).isEqualTo("a***@hyeja.kr");
    }
}
