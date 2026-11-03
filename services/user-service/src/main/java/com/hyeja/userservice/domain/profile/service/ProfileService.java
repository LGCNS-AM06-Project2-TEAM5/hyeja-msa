package com.hyeja.userservice.domain.profile.service;

import com.hyeja.userservice.domain.member.entity.Member;
import com.hyeja.userservice.domain.member.repository.MemberRepository;
import com.hyeja.userservice.domain.profile.converter.ProfileConverter;
import com.hyeja.userservice.domain.profile.dto.MemberProfileDTO;
import com.hyeja.userservice.domain.profile.dto.ProfileRequestDTO;
import com.hyeja.userservice.domain.profile.dto.ProfileResponseDTO;
import com.hyeja.userservice.domain.profile.entity.Profile;
import com.hyeja.userservice.domain.profile.repository.ProfileRepository;
import com.hyeja.userservice.domain.region.dto.RegionDTO;
import com.hyeja.userservice.domain.region.entity.Region;
import com.hyeja.userservice.domain.region.repository.RegionRepository;
import com.hyeja.userservice.global.apiPayload.status.ErrorStatus;
import com.hyeja.userservice.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProfileService {

    private final ProfileRepository profileRepository;
    private final RegionRepository regionRepository;
    private final MemberRepository memberRepository;
    // 만 나이 계산 기준일을 한국 날짜로 구합니다. ClockConfig의 koreaClock 빈입니다.
    private final Clock clock;

    /**
     * 회원가입에서 Member를 저장한 직후 호출합니다.
     * 회원가입과 같은 트랜잭션에서 실행되므로, 지역 코드가 region에 없으면
     * REGION_NOT_FOUND(404)를 던지고 Member 저장도 함께 롤백됩니다.
     */
    @Transactional
    public Profile create(Member member, ProfileRequestDTO request) {
        validateRegion(request.getRegionCode());
        return profileRepository.save(Profile.builder()
                .member(member)
                .regionCode(request.getRegionCode())
                .birth(request.getBirth())
                .employmentCode(request.getEmploymentCode())
                .houselessYn(request.getHouselessYn())
                .marriageCode(request.getMarriageCode())
                .incomeRangeCode(request.getIncomeRangeCode())
                .educationCode(request.getEducationCode())
                .housingType(request.getHousingType())
                .build());
    }

    /**
     * 내부 API(F1·F6)용 회원 조건 조회입니다.
     * 지역 이름은 user_db의 region 복사본에서 채웁니다.
     */
    public MemberProfileDTO getMemberProfile(Long memberId) {
        Profile profile = getActiveProfile(memberId);
        String sigunguName = findSigunguName(profile.getRegionCode());

        return MemberProfileDTO.builder()
                .memberId(memberId)
                .birth(profile.getBirth())
                .region(new RegionDTO(profile.getRegionCode(), sigunguName))
                .employmentCode(profile.getEmploymentCode())
                .houselessYn(profile.getHouselessYn())
                .marriageCode(profile.getMarriageCode())
                .incomeRangeCode(profile.getIncomeRangeCode())
                .educationCode(profile.getEducationCode())
                .housingType(profile.getHousingType())
                .build();
    }

    /**
     * 내 조건 조회입니다. 회원 ID는 컨트롤러가 토큰에서 꺼내 넘겨줍니다.
     * 회원이 없거나 탈퇴했으면 MEMBER_NOT_FOUND(404), 조건이 없으면 PROFILE_NOT_FOUND(404)를 던집니다.
     * 프론트는 PROFILE_NOT_FOUND를 받으면 온보딩으로 이동합니다.
     */
    public ProfileResponseDTO getMyProfile(Long memberId) {
        Profile profile = getActiveProfile(memberId);
        return ProfileConverter.toProfileResponseDTO(
                profile, findSigunguName(profile.getRegionCode()), LocalDate.now(clock));
    }

    /**
     * 내 조건을 전체 교체하고, 바뀐 조건을 조회 응답과 같은 형식으로 돌려줍니다.
     * 지역 코드가 region에 없으면 REGION_NOT_FOUND(404)를 던지고 아무것도 바꾸지 않습니다.
     */
    @Transactional
    public ProfileResponseDTO updateMyProfile(Long memberId, ProfileRequestDTO request) {
        Profile profile = getActiveProfile(memberId);
        validateRegion(request.getRegionCode());
        profile.update(request.getRegionCode(), request.getBirth(), request.getEmploymentCode(),
                request.getHouselessYn(), request.getMarriageCode(), request.getIncomeRangeCode(),
                request.getEducationCode(), request.getHousingType());
        // 수정 시각(updated_at)은 DB에 반영(flush)될 때 채워지므로, 응답에 새 시각이 담기도록 먼저 반영합니다.
        profileRepository.flush();
        return ProfileConverter.toProfileResponseDTO(
                profile, findSigunguName(profile.getRegionCode()), LocalDate.now(clock));
    }

    /**
     * 탈퇴하지 않은 회원의, 삭제되지 않은 조건을 조회합니다. 내부 API와 내 조건 조회·수정이 함께 씁니다.
     * 회원이 없거나 탈퇴했으면 MEMBER_NOT_FOUND(404), 조건이 없으면 PROFILE_NOT_FOUND(404)를 던집니다.
     * MemberService가 회원가입에서 이 서비스를 쓰므로, 순환 의존을 피하려고 MemberRepository로 직접 확인합니다.
     */
    public Profile getActiveProfile(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .filter(m -> !m.isDeleted())
                .orElseThrow(() -> new GeneralException(ErrorStatus.MEMBER_NOT_FOUND));

        // 프로필 PK는 회원 이메일입니다. 삭제 기록(deleted_at)이 있는 프로필도 미등록으로 봅니다.
        return profileRepository.findById(member.getEmail())
                .filter(p -> !p.isDeleted())
                .orElseThrow(() -> new GeneralException(ErrorStatus.PROFILE_NOT_FOUND));
    }

    // 지역 이름은 user_db의 region 복사본에서 찾고, 코드가 없으면 null입니다.
    private String findSigunguName(String regionCode) {
        return regionRepository.findById(regionCode)
                .map(Region::getSigunguName)
                .orElse(null);
    }

    // region은 user_db의 읽기 전용 복사본이라 policy-service를 호출하지 않고 바로 확인합니다.
    private void validateRegion(String regionCode) {
        if (!regionRepository.existsById(regionCode)) {
            throw new GeneralException(ErrorStatus.REGION_NOT_FOUND);
        }
    }
}
