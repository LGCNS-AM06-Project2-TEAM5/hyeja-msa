package com.hyeja.recommendationservice.policy.client;

import com.hyeja.recommendationservice.policy.dto.SearchPolicyDTO;
import com.hyeja.recommendationservice.policy.enums.PolicyCategory;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

// F2 (recommendation → policy). 구현은 Feign 연동 단계에서 추가합니다.
public interface PolicyClient {

    /**
     * 1차 PolicyRepository.searchActivePolicies와 같은 조건의 후보입니다.
     * 삭제·비활성·마감(CLOSED) 제외, 신청 기간에 today 포함, categories 중 하나라도 포함,
     * 정책 지역이 없거나 회원 지역(regionCode)과 같거나 같은 시도의 "xx000" 지역이 있는 정책.
     * 정렬은 마감일 오름차순(null 마지막) → 정책 ID 오름차순이며, 정렬한 뒤 앞에서 limit건만 돌려줍니다.
     * 장애를 빈 목록으로 바꿔 돌려주면 안 됩니다.
     */
    List<SearchPolicyDTO> searchActivePolicies(
            Set<PolicyCategory> categories, String regionCode, LocalDate today, int limit);
}
