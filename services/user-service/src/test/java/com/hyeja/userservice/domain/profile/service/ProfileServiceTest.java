package com.hyeja.userservice.domain.profile.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hyeja.userservice.domain.member.entity.Member;
import com.hyeja.userservice.domain.member.repository.MemberRepository;
import com.hyeja.userservice.domain.profile.dto.MemberProfileDTO;
import com.hyeja.userservice.domain.profile.dto.ProfileRequestDTO;
import com.hyeja.userservice.domain.profile.dto.ProfileResponseDTO;
import com.hyeja.userservice.domain.profile.entity.Profile;
import com.hyeja.userservice.domain.profile.enums.EmploymentStatus;
import com.hyeja.userservice.domain.profile.enums.IncomeRange;
import com.hyeja.userservice.domain.profile.enums.MaritalStatus;
import com.hyeja.userservice.domain.profile.repository.ProfileRepository;
import com.hyeja.userservice.domain.region.entity.Region;
import com.hyeja.userservice.domain.region.repository.RegionRepository;
import com.hyeja.userservice.global.apiPayload.status.ErrorStatus;
import com.hyeja.userservice.global.config.JpaAuditingConfig;
import com.hyeja.userservice.global.exception.GeneralException;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@Import({JpaAuditingConfig.class, ProfileServiceTest.FixedClockConfig.class, ProfileService.class})
class ProfileServiceTest {

    @Autowired
    private ProfileService profileService;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private RegionRepository regionRepository;

    @Autowired
    private EntityManager entityManager;

    private Member member;

    // 지역 2건과 회원 1명을 미리 저장합니다.
    @BeforeEach
    void setUp() {
        regionRepository.save(Region.builder().regionCode("11110").sigunguName("서울특별시 종로구").build());
        regionRepository.save(Region.builder().regionCode("11440").sigunguName("서울특별시 마포구").build());
        member = memberRepository.saveAndFlush(Member.builder()
                .email("user@hyeja.com")
                .password("encoded-password")
                .nickname("혜자")
                .build());
    }

    private ProfileRequestDTO request(String regionCode) {
        return ProfileRequestDTO.builder()
                .birth(LocalDate.of(2000, 1, 1))
                .regionCode(regionCode)
                .employmentCode(EmploymentStatus.EMPLOYED)
                .houselessYn(true)
                .incomeRangeCode(IncomeRange.R2000_3000)
                .build();
    }

    @Test
    @DisplayName("지역 코드가 있으면 회원 이메일을 키로 프로필을 저장한다")
    void create() {
        profileService.create(member, request("11110"));
        // 영속성 컨텍스트를 비워서, 메모리의 객체가 아니라 DB에 실제로 저장된 값을 다시 읽습니다.
        entityManager.flush();
        entityManager.clear();

        assertThat(profileRepository.findById("user@hyeja.com"))
                .hasValueSatisfying(profile -> {
                    assertThat(profile.getRegionCode()).isEqualTo("11110");
                    assertThat(profile.getBirth()).isEqualTo(LocalDate.of(2000, 1, 1));
                    assertThat(profile.getEmploymentCode()).isEqualTo(EmploymentStatus.EMPLOYED);
                    assertThat(profile.getHouselessYn()).isTrue();
                    assertThat(profile.getIncomeRangeCode()).isEqualTo(IncomeRange.R2000_3000);
                    assertThat(profile.getMarriageCode()).isNull();
                });
    }

    @Test
    @DisplayName("지역 코드가 region에 없으면 REGION_NOT_FOUND를 던지고 저장하지 않는다")
    void createWithUnknownRegion() {
        assertThatThrownBy(() -> profileService.create(member, request("99999")))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(ErrorStatus.REGION_NOT_FOUND);

        assertThat(profileRepository.count()).isZero();
    }

    @Test
    @DisplayName("내부 API용 조회는 지역 이름을 region 복사본에서 채워 돌려준다")
    void getMemberProfile() {
        profileService.create(member, request("11110"));

        MemberProfileDTO result = profileService.getMemberProfile(member.getMemberId());

        assertThat(result.getMemberId()).isEqualTo(member.getMemberId());
        assertThat(result.getRegion().getRegionCode()).isEqualTo("11110");
        assertThat(result.getRegion().getSigunguName()).isEqualTo("서울특별시 종로구");
        assertThat(result.getIncomeRangeCode()).isEqualTo(IncomeRange.R2000_3000);
        assertThat(result.getMarriageCode()).isNull();
    }

    @Test
    @DisplayName("탈퇴한 회원이면 MEMBER_NOT_FOUND를 던진다")
    void getMemberProfileOfWithdrawnMember() {
        profileService.create(member, request("11110"));
        member.softDelete();
        memberRepository.flush();

        assertThatThrownBy(() -> profileService.getMemberProfile(member.getMemberId()))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(ErrorStatus.MEMBER_NOT_FOUND);
    }

    @Test
    @DisplayName("조건을 등록하지 않았으면 PROFILE_NOT_FOUND, 없는 회원이면 MEMBER_NOT_FOUND를 던진다")
    void getMemberProfileWithoutProfile() {
        assertThatThrownBy(() -> profileService.getMemberProfile(member.getMemberId()))
                .extracting("code")
                .isEqualTo(ErrorStatus.PROFILE_NOT_FOUND);
        assertThatThrownBy(() -> profileService.getMemberProfile(999L))
                .extracting("code")
                .isEqualTo(ErrorStatus.MEMBER_NOT_FOUND);
    }

    @Test
    @DisplayName("조건이 삭제됐으면 PROFILE_NOT_FOUND를 던진다")
    void getMemberProfileWithDeletedProfile() {
        Profile profile = profileService.create(member, request("11110"));
        profile.softDelete();
        profileRepository.flush();

        assertThatThrownBy(() -> profileService.getMemberProfile(member.getMemberId()))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(ErrorStatus.PROFILE_NOT_FOUND);
    }

    @Test
    @DisplayName("내 조건 조회는 만 나이·지역 이름·한글 이름을 채워 돌려준다")
    void getMyProfile() {
        profileService.create(member, request("11110"));

        ProfileResponseDTO result = profileService.getMyProfile(member.getMemberId());

        assertThat(result.getAge()).isEqualTo(26);
        assertThat(result.getRegionCode()).isEqualTo("11110");
        assertThat(result.getRegionName()).isEqualTo("서울특별시 종로구");
        assertThat(result.getEmploymentName()).isEqualTo("재직자");
        assertThat(result.getIncomeRangeName()).isEqualTo("2천만원 이상 3천만원 미만");
        assertThat(result.getMarriageCode()).isNull();
        assertThat(result.getMarriageName()).isNull();
    }

    @Test
    @DisplayName("내 조건 수정은 조건 8개를 통째로 바꾸고, 보내지 않은 선택 항목은 지운다")
    void updateMyProfile() {
        profileService.create(member, request("11110"));
        // 소득 구간(incomeRangeCode)은 보내지 않아서 기존 값이 지워져야 합니다.
        ProfileRequestDTO update = ProfileRequestDTO.builder()
                .birth(LocalDate.of(1998, 5, 20))
                .regionCode("11440")
                .employmentCode(EmploymentStatus.UNEMPLOYED)
                .houselessYn(false)
                .marriageCode(MaritalStatus.MARRIED)
                .build();

        ProfileResponseDTO result = profileService.updateMyProfile(member.getMemberId(), update);

        assertThat(result.getAge()).isEqualTo(28);
        assertThat(result.getRegionName()).isEqualTo("서울특별시 마포구");

        entityManager.clear();
        assertThat(profileRepository.findById("user@hyeja.com"))
                .hasValueSatisfying(profile -> {
                    assertThat(profile.getRegionCode()).isEqualTo("11440");
                    assertThat(profile.getEmploymentCode()).isEqualTo(EmploymentStatus.UNEMPLOYED);
                    assertThat(profile.getHouselessYn()).isFalse();
                    assertThat(profile.getMarriageCode()).isEqualTo(MaritalStatus.MARRIED);
                    assertThat(profile.getIncomeRangeCode()).isNull();
                });
    }

    @Test
    @DisplayName("수정할 지역 코드가 region에 없으면 REGION_NOT_FOUND를 던지고 기존 조건을 유지한다")
    void updateMyProfileWithUnknownRegion() {
        profileService.create(member, request("11110"));

        assertThatThrownBy(() -> profileService.updateMyProfile(member.getMemberId(), request("99999")))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(ErrorStatus.REGION_NOT_FOUND);

        entityManager.flush();
        entityManager.clear();
        assertThat(profileRepository.findById("user@hyeja.com"))
                .hasValueSatisfying(profile -> assertThat(profile.getRegionCode()).isEqualTo("11110"));
    }

    // 만 나이 계산이 실행 날짜에 따라 바뀌지 않도록 오늘을 2026-11-03(한국 날짜)로 고정합니다.
    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-11-03T00:00:00Z"), ZoneId.of("Asia/Seoul"));
        }
    }
}
