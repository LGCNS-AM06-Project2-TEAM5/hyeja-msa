package com.hyeja.userservice.domain.member.dto;

import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Builder
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class MemberFindEmailResponseDTO {

    private String email;

    private LocalDate joinedAt;
}
