package com.hyeja.recommendationservice.profile.client;

import com.hyeja.recommendationservice.global.apiPayload.ApiResponse;
import com.hyeja.recommendationservice.global.apiPayload.status.SuccessStatus;
import com.hyeja.recommendationservice.profile.dto.EligibilityProfileDTO;
import com.hyeja.recommendationservice.profile.dto.MemberProfileDTO;
import com.hyeja.recommendationservice.profile.dto.SearchProfileDTO;
import com.hyeja.recommendationservice.region.dto.RegionDTO;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// F1 Adapter: 검색 코어의 ProfileClient 호출을 user-service 회원 조건 API로 보내고 응답을 SearchProfileDTO로 바꿉니다.
// 404 MEMBER_001·PROFILE_001은 UserServiceErrorDecoder가 같은 코드의 GeneralException으로 바꾸고,
// 그 밖의 HTTP 오류·연결 실패·Timeout·역직렬화 실패는 FeignException 그대로 전파해 검색 실패(SERVER_001)가 되게 합니다.
// 회로가 열려 있으면 호출하지 않고 CallNotPermittedException을 던지며, 이 역시 검색 실패(SERVER_001)입니다.
@Component
@RequiredArgsConstructor
public class ProfileClientAdapter implements ProfileClient {
    static final String CIRCUIT_BREAKER = "recUserProfile";

    private final UserProfileFeignClient userProfileFeignClient;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    @Override
    public SearchProfileDTO getActiveProfile(Long memberId) {
        ApiResponse<MemberProfileDTO> response = circuitBreakerRegistry.circuitBreaker(CIRCUIT_BREAKER)
                .executeSupplier(() -> userProfileFeignClient.getMemberProfile(memberId));
        // 204·빈 본문·실패 형식·result 누락은 빈 조건으로 바꾸지 않고 검색 실패로 처리합니다.
        if (response == null || !Boolean.TRUE.equals(response.getIsSuccess())
                || !SuccessStatus.OK.getCode().equals(response.getCode()) || response.getResult() == null) {
            throw new IllegalStateException("user-service 회원 조건 응답이 정상 형식이 아닙니다.");
        }
        MemberProfileDTO profile = response.getResult();
        // 거주지 코드는 F2 후보 조회와 지역 판정에 꼭 필요합니다. sigunguName은 계약상 null일 수 있습니다.
        if (profile.region() == null || profile.region().regionCode() == null
                || profile.region().regionCode().isBlank()) {
            throw new IllegalStateException("user-service 회원 조건 응답에 거주지 코드가 없습니다.");
        }
        return new SearchProfileDTO(
                EligibilityProfileDTO.builder()
                        .birth(profile.birth())
                        .region(RegionDTO.builder()
                                .regionCode(profile.region().regionCode())
                                .sigunguName(profile.region().sigunguName())
                                .build())
                        .employmentCode(profile.employmentCode())
                        .houselessYn(profile.houselessYn())
                        .incomeRangeCode(profile.incomeRangeCode())
                        .build(),
                profile.marriageCode(),
                profile.educationCode(),
                profile.housingType());
    }
}
