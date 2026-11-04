package com.hyeja.userservice.domain.profile.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.hyeja.userservice.domain.member.entity.Member;
import com.hyeja.userservice.domain.member.repository.MemberRepository;
import com.hyeja.userservice.domain.profile.entity.Profile;
import com.hyeja.userservice.domain.profile.enums.EducationLevel;
import com.hyeja.userservice.domain.profile.enums.EmploymentStatus;
import com.hyeja.userservice.domain.profile.enums.HousingType;
import com.hyeja.userservice.domain.profile.enums.IncomeRange;
import com.hyeja.userservice.domain.profile.enums.MaritalStatus;
import com.hyeja.userservice.global.config.JpaAuditingConfig;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
class ProfileRepositoryTest {

    // Repository 주입과 도우미 메서드
    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private EntityManager entityManager;

    private Member savedMember() {
        return memberRepository.saveAndFlush(Member.builder()
                .email("user@hyeja.com")
                .password("encoded-password")
                .nickname("혜자")
                .build());
    }

    private Profile profile(Member member) {
        return Profile.builder()
                .member(member)
                .regionCode("11110")
                .birth(LocalDate.of(2000, 1, 1))
                .employmentCode(EmploymentStatus.EMPLOYED)
                .houselessYn(true)
                .marriageCode(MaritalStatus.SINGLE)
                .incomeRangeCode(IncomeRange.R2000_3000)
                .educationCode(EducationLevel.COLLEGE_GRADUATE)
                .housingType(HousingType.MONTHLY_RENT)
                .build();
    }

    @Test
    @DisplayName("프로필을 저장하면 회원 이메일을 키로 조건 값과 지역 코드를 다시 읽을 수 있다")
    void saveAndFind() {
        profileRepository.saveAndFlush(profile(savedMember()));
        // 1차 캐시를 비워서 DB에서 다시 읽도록 합니다.
        entityManager.clear();

        Profile found = profileRepository.findById("user@hyeja.com").orElseThrow();

        assertThat(found.getRegionCode()).isEqualTo("11110");
        assertThat(found.getEmploymentCode()).isEqualTo(EmploymentStatus.EMPLOYED);
        assertThat(found.getMarriageCode()).isEqualTo(MaritalStatus.SINGLE);
        assertThat(found.getIncomeRangeCode()).isEqualTo(IncomeRange.R2000_3000);
        assertThat(found.getEducationCode()).isEqualTo(EducationLevel.COLLEGE_GRADUATE);
        assertThat(found.getHousingType()).isEqualTo(HousingType.MONTHLY_RENT);
        assertThat(found.getMember().getNickname()).isEqualTo("혜자");
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("프로필을 수정하면 모든 조건이 바뀌고 선택 항목에 null을 주면 지워진다")
    void update() {
        profileRepository.saveAndFlush(profile(savedMember()));
        entityManager.clear();

        Profile found = profileRepository.findById("user@hyeja.com").orElseThrow();
        found.update("26110", LocalDate.of(1999, 5, 5), EmploymentStatus.FREELANCER, false,
                null, null, null, null);
        profileRepository.flush();
        entityManager.clear();

        Profile updated = profileRepository.findById("user@hyeja.com").orElseThrow();
        assertThat(updated.getRegionCode()).isEqualTo("26110");
        assertThat(updated.getEmploymentCode()).isEqualTo(EmploymentStatus.FREELANCER);
        assertThat(updated.getHouselessYn()).isFalse();
        assertThat(updated.getMarriageCode()).isNull();
        assertThat(updated.getHousingType()).isNull();
    }
}
