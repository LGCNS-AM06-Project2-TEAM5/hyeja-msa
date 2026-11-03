package com.hyeja.userservice.domain.member.converter;

import com.hyeja.userservice.domain.member.dto.MemberAccountResponseDTO;
import com.hyeja.userservice.domain.member.dto.MemberFindEmailResponseDTO;
import com.hyeja.userservice.domain.member.dto.MemberLoginResponseDTO;
import com.hyeja.userservice.domain.member.dto.MemberSignupRequestDTO;
import com.hyeja.userservice.domain.member.entity.Member;

public final class MemberConverter {

    private MemberConverter() {
    }

    public static Member toMember(MemberSignupRequestDTO request, String encodedPassword) {
        return Member.builder()
                .email(request.getEmail())
                .password(encodedPassword)
                .nickname(request.getNickname())
                .build();
    }

    public static MemberAccountResponseDTO toMemberAccountResponseDTO(Member member) {
        return MemberAccountResponseDTO.builder()
                .memberId(member.getMemberId())
                .email(member.getEmail())
                .nickname(member.getNickname())
                .createdAt(member.getCreatedAt())
                .build();
    }

    public static MemberLoginResponseDTO toLoginResponseDTO(Member member, String accessToken) {
        return MemberLoginResponseDTO.builder()
                .accessToken(accessToken)
                .memberId(member.getMemberId())
                .nickname(member.getNickname())
                .build();
    }

    public static MemberFindEmailResponseDTO toFindEmailResponseDTO(Member member) {
        return MemberFindEmailResponseDTO.builder()
                .email(maskEmail(member.getEmail()))
                .joinedAt(member.getCreatedAt().toLocalDate())
                .build();
    }

    static String maskEmail(String email) {
        int at = email.indexOf('@');
        int visible = at > 3 ? 3 : 1;
        return email.substring(0, visible) + "***" + email.substring(at);
    }
}
