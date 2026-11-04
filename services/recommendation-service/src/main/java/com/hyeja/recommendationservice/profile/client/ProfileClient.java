package com.hyeja.recommendationservice.profile.client;

import com.hyeja.recommendationservice.profile.dto.SearchProfileDTO;

// F1 (recommendation → user). 구현은 Feign 연동 단계에서 추가합니다.
public interface ProfileClient {

    /**
     * 1차 ProfileService.getActiveProfile과 같은 기준의 회원 조건입니다.
     * 탈퇴·없는 회원은 MEMBER_001(404), 조건이 없거나 삭제됐으면 PROFILE_001(404)을 그대로 던져야 하며,
     * 장애를 빈 조건으로 바꿔 돌려주면 안 됩니다.
     */
    SearchProfileDTO getActiveProfile(Long memberId);
}
