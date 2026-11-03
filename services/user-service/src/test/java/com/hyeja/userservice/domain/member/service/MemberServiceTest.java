package com.hyeja.userservice.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hyeja.userservice.domain.member.dto.MemberAccountResponseDTO;
import com.hyeja.userservice.domain.member.dto.MemberFindEmailResponseDTO;
import com.hyeja.userservice.domain.member.dto.MemberLoginRequestDTO;
import com.hyeja.userservice.domain.member.dto.MemberLoginResponseDTO;
import com.hyeja.userservice.domain.member.dto.MemberSignupRequestDTO;
import com.hyeja.userservice.domain.member.entity.Member;
import com.hyeja.userservice.domain.member.event.MemberWithdrawnPublisher;
import com.hyeja.userservice.domain.member.repository.MemberRepository;
import com.hyeja.userservice.domain.profile.dto.ProfileRequestDTO;
import com.hyeja.userservice.domain.profile.entity.Profile;
import com.hyeja.userservice.domain.profile.enums.EmploymentStatus;
import com.hyeja.userservice.domain.profile.repository.ProfileRepository;
import com.hyeja.userservice.domain.profile.service.ProfileService;
import com.hyeja.userservice.global.apiPayload.status.ErrorStatus;
import com.hyeja.userservice.global.exception.GeneralException;
import com.hyeja.userservice.global.security.JwtProvider;
import com.hyeja.userservice.global.security.TokenBlacklist;
import io.jsonwebtoken.Jwts;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock
    private MemberRepository memberRepository;
    @Mock
    private ProfileRepository profileRepository;
    @Mock
    private ProfileService profileService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtProvider jwtProvider;
    @Mock
    private TokenBlacklist tokenBlacklist;
    @Mock
    private EmailVerificationService emailVerificationService;
    @Mock
    private MemberWithdrawnPublisher memberWithdrawnPublisher;
    @InjectMocks
    private MemberService memberService;

    @Test
    void signupSavesEncodedMemberAndProfileAfterEmailVerification() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 11, 3, 10, 0);
        MemberSignupRequestDTO request = signupRequest("11440");
        when(memberRepository.existsByEmail("hyeja@example.com")).thenReturn(false);
        when(memberRepository.existsByNickname("민지")).thenReturn(false);
        when(passwordEncoder.encode("hyeja1234!")).thenReturn("encoded-password");
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> {
            Member saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "memberId", 1L);
            ReflectionTestUtils.setField(saved, "createdAt", createdAt);
            return saved;
        });

        MemberAccountResponseDTO result = memberService.signup(request);

        ArgumentCaptor<Member> memberCaptor = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).save(memberCaptor.capture());
        Member savedMember = memberCaptor.getValue();
        assertThat(savedMember.getPassword()).isEqualTo("encoded-password");
        assertThat(result.getMemberId()).isEqualTo(1L);
        assertThat(result.getCreatedAt()).isEqualTo(createdAt);
        verify(emailVerificationService).checkVerified("hyeja@example.com");
        verify(profileService).create(savedMember, request.getProfile());
        verify(emailVerificationService).clearVerified("hyeja@example.com");
    }

    @Test
    void signupRejectsDuplicatedEmailBeforeVerification() {
        when(memberRepository.existsByEmail("hyeja@example.com")).thenReturn(true);

        assertSignupFails(signupRequest("11440"), ErrorStatus.MEMBER_EMAIL_DUPLICATED);

        verify(emailVerificationService, never()).checkVerified(any());
    }

    @Test
    void signupRejectsUnverifiedEmail() {
        when(memberRepository.existsByEmail("hyeja@example.com")).thenReturn(false);
        doThrow(new GeneralException(ErrorStatus.VERIFY_REQUIRED))
                .when(emailVerificationService).checkVerified("hyeja@example.com");

        assertSignupFails(signupRequest("11440"), ErrorStatus.VERIFY_REQUIRED);
    }

    @Test
    void signupRejectsDuplicatedNickname() {
        when(memberRepository.existsByEmail("hyeja@example.com")).thenReturn(false);
        when(memberRepository.existsByNickname("민지")).thenReturn(true);

        assertSignupFails(signupRequest("11440"), ErrorStatus.MEMBER_NICKNAME_DUPLICATED);
    }

    @Test
    void signupDoesNotClearVerificationWhenProfileCreationFails() {
        MemberSignupRequestDTO request = signupRequest("99999");
        when(memberRepository.existsByEmail("hyeja@example.com")).thenReturn(false);
        when(memberRepository.existsByNickname("민지")).thenReturn(false);
        when(passwordEncoder.encode("hyeja1234!")).thenReturn("encoded-password");
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new GeneralException(ErrorStatus.REGION_NOT_FOUND))
                .when(profileService).create(any(Member.class), any(ProfileRequestDTO.class));

        assertSignupFails(request, ErrorStatus.REGION_NOT_FOUND);

        verify(emailVerificationService, never()).clearVerified(any());
    }

    @Test
    void loginReturnsAccessTokenWhenCredentialsMatch() {
        Member member = member(1L, LocalDateTime.now());
        when(memberRepository.findByEmail("hyeja@example.com")).thenReturn(Optional.of(member));
        when(passwordEncoder.matches("hyeja1234!", "encoded-password")).thenReturn(true);
        when(jwtProvider.createAccessToken(member)).thenReturn("access-token");

        MemberLoginResponseDTO result = memberService.login(loginRequest("hyeja@example.com", "hyeja1234!"));

        assertThat(result.getAccessToken()).isEqualTo("access-token");
        assertThat(result.getMemberId()).isEqualTo(1L);
        assertThat(result.getNickname()).isEqualTo("민지");
    }

    @Test
    void loginUsesSameErrorForUnknownEmailWrongPasswordAndDeletedMember() {
        when(memberRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());
        assertLoginFails(loginRequest("unknown@example.com", "hyeja1234!"));

        Member active = member(1L, LocalDateTime.now());
        when(memberRepository.findByEmail("hyeja@example.com")).thenReturn(Optional.of(active));
        when(passwordEncoder.matches("wrong-password", "encoded-password")).thenReturn(false);
        assertLoginFails(loginRequest("hyeja@example.com", "wrong-password"));

        Member deleted = member(2L, LocalDateTime.now());
        deleted.softDelete();
        when(memberRepository.findByEmail("deleted@example.com")).thenReturn(Optional.of(deleted));
        assertLoginFails(loginRequest("deleted@example.com", "hyeja1234!"));

        verify(jwtProvider, never()).createAccessToken(any());
    }

    @Test
    void logoutBlacklistsTokenUntilExpiration() {
        Date expiration = new Date(System.currentTimeMillis() + Duration.ofMinutes(10).toMillis());
        when(jwtProvider.parse("access-token"))
                .thenReturn(Jwts.claims().expiration(expiration).build());

        memberService.logout("access-token");

        ArgumentCaptor<Duration> remaining = ArgumentCaptor.forClass(Duration.class);
        verify(tokenBlacklist).add(eq("access-token"), remaining.capture());
        assertThat(remaining.getValue()).isBetween(Duration.ofMinutes(9), Duration.ofMinutes(10));
    }

    @Test
    void withdrawSoftDeletesMemberAndProfileAndBlacklistsToken() {
        Member member = member(1L, LocalDateTime.now());
        Profile profile = profile(member, LocalDate.of(2000, 3, 15));
        Date expiration = new Date(System.currentTimeMillis() + Duration.ofMinutes(10).toMillis());
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(passwordEncoder.matches("hyeja1234!", "encoded-password")).thenReturn(true);
        when(profileRepository.findById("hyeja@example.com")).thenReturn(Optional.of(profile));
        when(jwtProvider.parse("access-token"))
                .thenReturn(Jwts.claims().expiration(expiration).build());

        memberService.withdraw(1L, "hyeja1234!", "access-token");

        assertThat(member.isDeleted()).isTrue();
        assertThat(profile.isDeleted()).isTrue();
        verify(memberWithdrawnPublisher).publish(1L);
        verify(tokenBlacklist).add(eq("access-token"), any(Duration.class));
    }

    @Test
    void withdrawRejectsWrongPasswordWithoutChangingDataOrToken() {
        Member member = member(1L, LocalDateTime.now());
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(passwordEncoder.matches("wrong-password", "encoded-password")).thenReturn(false);

        assertThatThrownBy(() -> memberService.withdraw(1L, "wrong-password", "access-token"))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(ErrorStatus.MEMBER_PASSWORD_MISMATCH);

        assertThat(member.isDeleted()).isFalse();
        verify(profileRepository, never()).findById(any());
        verify(memberWithdrawnPublisher, never()).publish(any());
        verify(tokenBlacklist, never()).add(any(), any());
    }

    @Test
    void withdrawRejectsMissingOrAlreadyDeletedMember() {
        when(memberRepository.findById(99L)).thenReturn(Optional.empty());
        assertWithdrawalMemberNotFound(99L);

        Member deleted = member(2L, LocalDateTime.now());
        deleted.softDelete();
        when(memberRepository.findById(2L)).thenReturn(Optional.of(deleted));
        assertWithdrawalMemberNotFound(2L);

        verify(passwordEncoder, never()).matches(any(), any());
        verify(memberWithdrawnPublisher, never()).publish(any());
        verify(tokenBlacklist, never()).add(any(), any());
    }

    @Test
    void getMyAccountReturnsOnlyActiveMember() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 11, 2, 16, 30);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member(1L, createdAt)));

        MemberAccountResponseDTO result = memberService.getMyAccount(1L);

        assertThat(result.getMemberId()).isEqualTo(1L);
        assertThat(result.getEmail()).isEqualTo("hyeja@example.com");
        assertThat(result.getNickname()).isEqualTo("민지");
        assertThat(result.getCreatedAt()).isEqualTo(createdAt);
    }

    @Test
    void getMyAccountRejectsMissingAndDeletedMember() {
        when(memberRepository.findById(99L)).thenReturn(Optional.empty());
        assertMemberNotFound(99L);

        Member deleted = member(1L, LocalDateTime.now());
        deleted.softDelete();
        when(memberRepository.findById(1L)).thenReturn(Optional.of(deleted));
        assertMemberNotFound(1L);
    }

    @Test
    void findEmailReturnsMaskedEmailWhenNicknameAndBirthMatch() {
        Member member = member(1L, LocalDateTime.of(2026, 9, 20, 14, 3, 11));
        when(memberRepository.findByNickname("민지")).thenReturn(Optional.of(member));
        when(profileRepository.findById("hyeja@example.com"))
                .thenReturn(Optional.of(profile(member, LocalDate.of(2000, 3, 15))));

        MemberFindEmailResponseDTO result = memberService.findEmail("민지", LocalDate.of(2000, 3, 15));

        assertThat(result.getEmail()).isEqualTo("hye***@example.com");
        assertThat(result.getJoinedAt()).isEqualTo(LocalDate.of(2026, 9, 20));
    }

    @Test
    void findEmailUsesSameErrorForUnknownNicknameWrongBirthAndDeletedProfile() {
        when(memberRepository.findByNickname("없는닉네임")).thenReturn(Optional.empty());
        assertFindEmailFails("없는닉네임", LocalDate.of(2000, 3, 15));

        Member member = member(1L, LocalDateTime.now());
        Profile profile = profile(member, LocalDate.of(2000, 3, 15));
        when(memberRepository.findByNickname("민지")).thenReturn(Optional.of(member));
        when(profileRepository.findById("hyeja@example.com")).thenReturn(Optional.of(profile));
        assertFindEmailFails("민지", LocalDate.of(1999, 1, 1));

        profile.softDelete();
        assertFindEmailFails("민지", LocalDate.of(2000, 3, 15));
    }

    private void assertSignupFails(MemberSignupRequestDTO request, ErrorStatus expected) {
        assertThatThrownBy(() -> memberService.signup(request))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(expected);
        verify(emailVerificationService, never()).clearVerified(any());
    }

    private MemberSignupRequestDTO signupRequest(String regionCode) {
        return MemberSignupRequestDTO.builder()
                .email("hyeja@example.com")
                .password("hyeja1234!")
                .nickname("민지")
                .profile(ProfileRequestDTO.builder()
                        .birth(LocalDate.of(2000, 3, 15))
                        .regionCode(regionCode)
                        .employmentCode(EmploymentStatus.EMPLOYED)
                        .houselessYn(true)
                        .build())
                .build();
    }

    private void assertLoginFails(MemberLoginRequestDTO request) {
        assertThatThrownBy(() -> memberService.login(request))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(ErrorStatus.MEMBER_LOGIN_FAILED);
    }

    private void assertMemberNotFound(Long memberId) {
        assertThatThrownBy(() -> memberService.getMyAccount(memberId))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(ErrorStatus.MEMBER_NOT_FOUND);
    }

    private void assertWithdrawalMemberNotFound(Long memberId) {
        assertThatThrownBy(() -> memberService.withdraw(memberId, "hyeja1234!", "access-token"))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(ErrorStatus.MEMBER_NOT_FOUND);
    }

    private void assertFindEmailFails(String nickname, LocalDate birth) {
        assertThatThrownBy(() -> memberService.findEmail(nickname, birth))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(ErrorStatus.MEMBER_EMAIL_NOT_FOUND);
    }

    private MemberLoginRequestDTO loginRequest(String email, String password) {
        return MemberLoginRequestDTO.builder().email(email).password(password).build();
    }

    private Profile profile(Member member, LocalDate birth) {
        return Profile.builder()
                .member(member)
                .regionCode("11440")
                .birth(birth)
                .employmentCode(EmploymentStatus.EMPLOYED)
                .houselessYn(true)
                .build();
    }

    private Member member(Long memberId, LocalDateTime createdAt) {
        Member member = Member.builder()
                .email(memberId == 2L ? "deleted@example.com" : "hyeja@example.com")
                .password("encoded-password")
                .nickname("민지")
                .build();
        ReflectionTestUtils.setField(member, "memberId", memberId);
        ReflectionTestUtils.setField(member, "createdAt", createdAt);
        return member;
    }
}
