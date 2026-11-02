package com.hyeja.userservice.domain.profile.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hyeja.userservice.domain.member.entity.Member;
import com.hyeja.userservice.domain.member.repository.MemberRepository;
import com.hyeja.userservice.domain.profile.dto.MemberProfileDTO;
import com.hyeja.userservice.domain.profile.dto.ProfileRequestDTO;
import com.hyeja.userservice.domain.profile.entity.Profile;
import com.hyeja.userservice.domain.profile.enums.EmploymentStatus;
import com.hyeja.userservice.domain.profile.enums.IncomeRange;
import com.hyeja.userservice.domain.profile.repository.ProfileRepository;
import com.hyeja.userservice.domain.region.entity.Region;
import com.hyeja.userservice.domain.region.repository.RegionRepository;
import com.hyeja.userservice.global.apiPayload.status.ErrorStatus;
import com.hyeja.userservice.global.config.JpaAuditingConfig;
import com.hyeja.userservice.global.exception.GeneralException;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@Import({JpaAuditingConfig.class, ProfileService.class})
class ProfileServiceTest {

    @Autowired
    private ProfileService profileService;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private RegionRepository regionRepository;

    private Member member;

    // 지역 1건과 회원 1명을 미리 저장합니다.
    @BeforeEach
    void setUp() {
        regionRepository.save(Region.builder().regionCode("11110").sigunguName("서울특별시 종로구").build());
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

        assertThat(profileRepository.findById("user@hyeja.com"))
                .hasValueSatisfying(profile -> {
                    assertThat(profile.getRegionCode()).isEqualTo("11110");
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
}
