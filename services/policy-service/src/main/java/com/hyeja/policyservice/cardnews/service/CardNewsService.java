package com.hyeja.policyservice.cardnews.service;

import com.hyeja.policyservice.cardnews.dto.CardNewsDetailResponseDTO;
import com.hyeja.policyservice.cardnews.dto.CardNewsDetailResponseDTO.CardDTO;
import com.hyeja.policyservice.cardnews.dto.CardNewsResponseDTO;
import com.hyeja.policyservice.cardnews.dto.MemberCardNewsResponseDTO;
import com.hyeja.policyservice.client.user.MemberProfileReader;
import com.hyeja.policyservice.client.user.dto.MemberProfileDTO;
import com.hyeja.policyservice.cardnews.entity.CardNews;
import com.hyeja.policyservice.cardnews.repository.CardNewsRepository;
import com.hyeja.policyservice.global.apiPayload.status.ErrorStatus;
import com.hyeja.policyservice.global.exception.GeneralException;
import com.hyeja.policyservice.policy.entity.Policy;
import com.hyeja.policyservice.policy.entity.PolicyRegion;
import com.hyeja.policyservice.policy.repository.PolicyRegionRepository;
import com.hyeja.policyservice.region.converter.RegionConverter;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CardNewsService {

    private final CardNewsRepository cardNewsRepository;
    private final PolicyRegionRepository policyRegionRepository;
    private final MemberProfileReader memberProfileReader;
    // 한국 시간(Asia/Seoul) 기준 오늘 날짜. ClockConfig의 koreaClock 빈입니다.
    private final Clock clock;

    public List<CardNewsResponseDTO> getGuestCardNews() {
        // 대표 카드(card_no = 1)를 마감일 순으로 상위 4개 조회
        List<CardNews> cardNewsList = guestHomeCards();

        return cardNewsList.stream()
                .map(cn -> CardNewsResponseDTO.builder()
                        .policyId(cn.getPolicy().getPolicyId())
                        .policyName(cn.getPolicy().getPolicyName())
                        .description(cn.getPolicy().getDescription())
                        .applyEndDate(cn.getPolicy().getApplyEndDate() != null ? cn.getPolicy().getApplyEndDate().toString() : null)
                        .build())
                .toList();
    }

    // user(F6) 응답을 기다리는 동안 DB 커넥션을 잡지 않도록 트랜잭션 밖에서 부릅니다. 두 홈 쿼리는 policy를 join fetch합니다.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<MemberCardNewsResponseDTO> getMemberCardNews(Long memberId) {
        // 정책 목록·상세와 같은 기준으로, 탈퇴 회원은 MEMBER_001, 조건이 없거나 삭제됐으면 PROFILE_001입니다.
        // user가 응답하지 않으면(F6 계약의 장애 시 동작) 비로그인 홈 카드뉴스로 대신합니다.
        MemberProfileDTO profile;
        try {
            profile = memberProfileReader.getActiveProfile(memberId);
        } catch (GeneralException e) {
            if (e.getCode() != ErrorStatus.USER_SERVICE_UNAVAILABLE) {
                throw e;
            }
            log.warn("회원 조건을 불러오지 못해 비로그인 홈 카드뉴스로 응답합니다. memberId={}", memberId);
            return guestHomeCards().stream().map(this::toMemberCard).toList();
        }
        String regionCode = profile.region().regionCode();

        return cardNewsRepository.findMemberHomeCardNews(
                        regionCode, regionCode.substring(0, 2) + "000",
                        LocalDate.now(clock), PageRequest.of(0, 4)).stream()
                .map(this::toMemberCard)
                .toList();
    }

    private List<CardNews> guestHomeCards() {
        return cardNewsRepository.findGuestHomeCardNews(LocalDate.now(clock), PageRequest.of(0, 4));
    }

    private MemberCardNewsResponseDTO toMemberCard(CardNews cardNews) {
        return new MemberCardNewsResponseDTO(
                cardNews.getPolicy().getPolicyId(),
                cardNews.getPolicy().getPolicyName(),
                cardNews.getPolicy().getDescription(),
                cardNews.getPolicy().getApplyEndDate());
    }

    public CardNewsDetailResponseDTO getCardNewsDetail(String policyId, Long memberId) {
        List<CardNews> cardNews = cardNewsRepository
                .findAllActiveByPolicyIdOrderByCardNo(policyId);
        if (cardNews.isEmpty()) {
            throw new GeneralException(ErrorStatus.CARD_NEWS_NOT_FOUND);
        }
        Policy policy = cardNews.get(0).getPolicy();
        List<PolicyRegion> policyRegions = policyRegionRepository
                .findAllActiveByPolicyIds(List.of(policy.getPolicyId()));
        List<String> eligibilityBadges = List.of(
                formatAge(policy), RegionConverter.summarize(
                        policyRegions.stream().map(PolicyRegion::getRegion).toList()));
        boolean authenticated = memberId != null;

        List<CardDTO> cards = cardNews.stream()
                .map(card -> new CardDTO(
                        card.getCardNewsId(),
                        card.getCardNo(),
                        card.getTitle(),
                        card.getCardNo() == 2L ? eligibilityBadges : List.of(),
                        card.getBody()))
                .toList();

        return new CardNewsDetailResponseDTO(
                policy.getPolicyId(),
                policy.getCategories().stream().sorted().findFirst()
                        .map(category -> category.getLabel()).orElse(null),
                calculateDDay(policy),
                authenticated,
                // 관심 여부는 engagement(F4) 연결 전까지 false입니다. 1차는 FavoriteRepository로 확인했습니다.
                false,
                policy.getApplyUrl(),
                cards);
    }

    private String formatAge(Policy policy) {
        if (!Boolean.TRUE.equals(policy.getAgeLimitYn())) return "연령 제한 없음";
        if (policy.getMinAge() != null && policy.getMaxAge() != null) {
            return "만 %d~%d세".formatted(policy.getMinAge(), policy.getMaxAge());
        }
        if (policy.getMinAge() != null) return "만 %d세 이상".formatted(policy.getMinAge());
        if (policy.getMaxAge() != null) return "만 %d세 이하".formatted(policy.getMaxAge());
        return "연령 제한 없음";
    }

    private Integer calculateDDay(Policy policy) {
        return policy.getApplyEndDate() == null ? null
                : Math.toIntExact(ChronoUnit.DAYS.between(
                        LocalDate.now(clock), policy.getApplyEndDate()));
    }
}
