package com.hyeja.userservice.domain.profile.service;

import com.hyeja.userservice.domain.member.entity.Member;
import com.hyeja.userservice.domain.profile.dto.ProfileRequestDTO;
import com.hyeja.userservice.domain.profile.entity.Profile;
import com.hyeja.userservice.domain.profile.repository.ProfileRepository;
import com.hyeja.userservice.domain.region.repository.RegionRepository;
import com.hyeja.userservice.global.apiPayload.status.ErrorStatus;
import com.hyeja.userservice.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProfileService {

    private final ProfileRepository profileRepository;
    private final RegionRepository regionRepository;

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

    // region은 user_db의 읽기 전용 복사본이라 policy-service를 호출하지 않고 바로 확인합니다.
    private void validateRegion(String regionCode) {
        if (!regionRepository.existsById(regionCode)) {
            throw new GeneralException(ErrorStatus.REGION_NOT_FOUND);
        }
    }
}
