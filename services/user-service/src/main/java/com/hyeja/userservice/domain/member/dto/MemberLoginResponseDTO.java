package com.hyeja.userservice.domain.member.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Builder
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class MemberLoginResponseDTO {

    private String accessToken;

    private Long memberId;

    private String nickname;
}
