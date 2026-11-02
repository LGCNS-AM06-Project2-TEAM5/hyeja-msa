package com.hyeja.userservice.domain.member.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Builder
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class MemberAccountResponseDTO {

    private Long memberId;

    private String email;

    private String nickname;

    private LocalDateTime createdAt;
}
