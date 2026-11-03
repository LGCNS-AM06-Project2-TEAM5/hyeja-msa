package com.hyeja.userservice.domain.member.service;

import com.hyeja.userservice.domain.member.converter.MemberConverter;
import com.hyeja.userservice.domain.member.dto.MemberAccountResponseDTO;
import com.hyeja.userservice.domain.member.dto.MemberFindEmailResponseDTO;
import com.hyeja.userservice.domain.member.dto.MemberLoginRequestDTO;
import com.hyeja.userservice.domain.member.dto.MemberLoginResponseDTO;
import com.hyeja.userservice.domain.member.dto.MemberSignupRequestDTO;
import com.hyeja.userservice.domain.member.entity.Member;
import com.hyeja.userservice.domain.member.repository.MemberRepository;
import com.hyeja.userservice.domain.profile.entity.Profile;
import com.hyeja.userservice.domain.profile.repository.ProfileRepository;
import com.hyeja.userservice.domain.profile.service.ProfileService;
import com.hyeja.userservice.global.apiPayload.status.ErrorStatus;
import com.hyeja.userservice.global.exception.GeneralException;
import com.hyeja.userservice.global.security.JwtProvider;
import com.hyeja.userservice.global.security.TokenBlacklist;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;
    private final ProfileRepository profileRepository;
    private final ProfileService profileService;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final TokenBlacklist tokenBlacklist;
    private final EmailVerificationService emailVerificationService;

    @Transactional
    public MemberAccountResponseDTO signup(MemberSignupRequestDTO request) {
        if (memberRepository.existsByEmail(request.getEmail())) {
            throw new GeneralException(ErrorStatus.MEMBER_EMAIL_DUPLICATED);
        }
        emailVerificationService.checkVerified(request.getEmail());
        if (memberRepository.existsByNickname(request.getNickname())) {
            throw new GeneralException(ErrorStatus.MEMBER_NICKNAME_DUPLICATED);
        }

        Member member = memberRepository.save(MemberConverter.toMember(
                request,
                passwordEncoder.encode(request.getPassword())
        ));
        profileService.create(member, request.getProfile());
        emailVerificationService.clearVerified(request.getEmail());

        return MemberConverter.toMemberAccountResponseDTO(member);
    }

    public MemberLoginResponseDTO login(MemberLoginRequestDTO request) {
        Member member = memberRepository.findByEmail(request.getEmail())
                .filter(candidate -> !candidate.isDeleted())
                .filter(candidate -> passwordEncoder.matches(request.getPassword(), candidate.getPassword()))
                .orElseThrow(() -> new GeneralException(ErrorStatus.MEMBER_LOGIN_FAILED));

        return MemberConverter.toLoginResponseDTO(member, jwtProvider.createAccessToken(member));
    }

    public void logout(String token) {
        Instant expiration = jwtProvider.parse(token).getExpiration().toInstant();
        tokenBlacklist.add(token, Duration.between(Instant.now(), expiration));
    }

    public MemberAccountResponseDTO getMyAccount(Long memberId) {
        return MemberConverter.toMemberAccountResponseDTO(getActiveMember(memberId));
    }

    @Transactional
    public void withdraw(Long memberId, String password, String token) {
        Member member = getActiveMember(memberId);
        if (!passwordEncoder.matches(password, member.getPassword())) {
            throw new GeneralException(ErrorStatus.MEMBER_PASSWORD_MISMATCH);
        }

        profileRepository.findById(member.getEmail()).ifPresent(Profile::softDelete);
        member.softDelete();
        logout(token);
    }

    public Member getActiveMember(Long memberId) {
        return memberRepository.findById(memberId)
                .filter(member -> !member.isDeleted())
                .orElseThrow(() -> new GeneralException(ErrorStatus.MEMBER_NOT_FOUND));
    }

    public MemberFindEmailResponseDTO findEmail(String nickname, LocalDate birth) {
        Member member = memberRepository.findByNickname(nickname)
                .filter(candidate -> !candidate.isDeleted())
                .filter(candidate -> profileRepository.findById(candidate.getEmail())
                        .filter(profile -> !profile.isDeleted() && birth.equals(profile.getBirth()))
                        .isPresent())
                .orElseThrow(() -> new GeneralException(ErrorStatus.MEMBER_EMAIL_NOT_FOUND));

        return MemberConverter.toFindEmailResponseDTO(member);
    }
}
