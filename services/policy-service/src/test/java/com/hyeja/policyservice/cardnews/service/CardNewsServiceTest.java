package com.hyeja.policyservice.cardnews.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

import com.hyeja.policyservice.cardnews.entity.CardNews;
import com.hyeja.policyservice.cardnews.repository.CardNewsRepository;
import com.hyeja.policyservice.policy.entity.Policy;
import com.hyeja.policyservice.policy.entity.PolicyRegion;
import com.hyeja.policyservice.policy.enums.PolicyApplyPeriod;
import com.hyeja.policyservice.policy.enums.PolicyCategory;
import com.hyeja.policyservice.policy.enums.PolicyHouselessRequirement;
import com.hyeja.policyservice.policy.repository.PolicyRegionRepository;
import com.hyeja.policyservice.region.entity.Region;
import com.hyeja.policyservice.global.apiPayload.status.ErrorStatus;
import com.hyeja.policyservice.global.exception.GeneralException;
import java.time.LocalDate;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CardNewsServiceTest {
    private final CardNewsRepository cardNewsRepository = mock(CardNewsRepository.class);
    private final PolicyRegionRepository policyRegionRepository = mock(PolicyRegionRepository.class);
    // 한국 2026-09-28 01:00 = UTC 2026-09-27 16:00. 서버 기본 시간대(UTC)로 계산하면 날짜가 하루 어긋나는 시각입니다.
    private static final Clock KOREA_CLOCK = Clock.fixed(
            Instant.parse("2026-09-27T16:00:00Z"), ZoneId.of("Asia/Seoul"));
    private final CardNewsService service = new CardNewsService(
            cardNewsRepository, policyRegionRepository, KOREA_CLOCK);

    @Test
    void returnsPopupDataForAuthenticatedMember() {
        Policy policy = policy();
        List<CardNews> cards = List.of(
                card(policy, 1L, "정책명", "정책 소개"),
                card(policy, 2L, null, "신청 대상"),
                card(policy, 3L, "지원 혜택", "혜택 설명"),
                card(policy, 4L, "2026-09-01 ~ 2026-09-30", "신청 방법"));
        Region region = Region.builder()
                .regionCode("11000").sigunguName("서울특별시").build();
        when(cardNewsRepository.findAllActiveByPolicyIdOrderByCardNo("policy-1"))
                .thenReturn(cards);
        when(policyRegionRepository.findAllActiveByPolicyIds(List.of("policy-1")))
                .thenReturn(List.of(PolicyRegion.builder().policy(policy).region(region).build()));

        var response = service.getCardNewsDetail("policy-1", 7L);

        assertThat(response.policyId()).isEqualTo("policy-1");
        assertThat(response.categoryLabel()).isEqualTo("월세");
        // 한국 날짜 09-28 기준 마감 10-03 → D-5 (UTC 날짜 09-27로 세면 D-6)
        assertThat(response.dDay()).isEqualTo(5);
        assertThat(response.isAuthenticated()).isTrue();
        // 관심 여부는 engagement(F4) 연결 전까지 false입니다.
        assertThat(response.isFavorite()).isFalse();
        assertThat(response.applyUrl()).isEqualTo("https://example.com/apply");
        assertThat(response.cards()).extracting(card -> card.cardNo())
                .containsExactly(1L, 2L, 3L, 4L);
        assertThat(response.cards().get(1).badges())
                .containsExactly("만 19~34세", "서울특별시");
        assertThat(response.cards().get(0).badges()).isEmpty();
    }

    // 지역이 여러 곳이면 첫 지역만 보여 주지 않고 "외 N곳"으로 요약합니다(정책 목록·상세와 같은 문구).
    @Test
    void summarizesMultipleRegionsInBadge() {
        Policy policy = policy();
        when(cardNewsRepository.findAllActiveByPolicyIdOrderByCardNo("policy-1"))
                .thenReturn(List.of(card(policy, 2L, null, "신청 대상")));
        when(policyRegionRepository.findAllActiveByPolicyIds(List.of("policy-1")))
                .thenReturn(List.of(
                        PolicyRegion.builder().policy(policy).region(Region.builder()
                                .regionCode("11110").sigunguName("서울특별시 종로구").build()).build(),
                        PolicyRegion.builder().policy(policy).region(Region.builder()
                                .regionCode("11140").sigunguName("서울특별시 중구").build()).build()));

        var response = service.getCardNewsDetail("policy-1", null);

        assertThat(response.cards().get(0).badges()).containsExactly("만 19~34세", "서울특별시 종로구 외 1곳");
    }

    @Test
    void treatsMissingPrincipalAsGuest() {
        Policy policy = policy();
        CardNews card = card(policy, 1L, "정책명", "정책 소개");
        when(cardNewsRepository.findAllActiveByPolicyIdOrderByCardNo("policy-1"))
                .thenReturn(List.of(card));
        when(policyRegionRepository.findAllActiveByPolicyIds(List.of("policy-1")))
                .thenReturn(List.of());

        var response = service.getCardNewsDetail("policy-1", null);

        assertThat(response.isAuthenticated()).isFalse();
        assertThat(response.isFavorite()).isFalse();
    }

    @Test
    void rejectsUnknownCardNews() {
        when(cardNewsRepository.findAllActiveByPolicyIdOrderByCardNo("missing-policy"))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.getCardNewsDetail("missing-policy", null))
                .isInstanceOfSatisfying(GeneralException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(ErrorStatus.CARD_NEWS_NOT_FOUND));
    }

    @Test
    void usesPolicyDescriptionInsteadOfCardBodyForHomeCards() {
        Policy policy = policy();
        CardNews card = card(policy, 1L, "정책명", "기호가 포함된 원문 | / <> ");
        when(cardNewsRepository.findGuestHomeCardNews(any(LocalDate.class), any()))
                .thenReturn(List.of(card));

        var response = service.getGuestCardNews();

        assertThat(response).singleElement()
                .extracting(item -> item.getDescription())
                .isEqualTo("정제된 정책 설명");
    }

    private Policy policy() {
        return Policy.builder()
                .policyId("policy-1")
                .policyName("청년 월세 지원")
                .description("정제된 정책 설명")
                .categories(Set.of(PolicyCategory.MONTHLY_RENT))
                .minAge(19)
                .maxAge(34)
                .ageLimitYn(true)
                .houselessRequirement(PolicyHouselessRequirement.UNKNOWN)
                .applyPeriodCode(PolicyApplyPeriod.SPECIFIC_PERIOD)
                .applyStartDate(LocalDate.of(2026, 9, 23))
                .applyEndDate(LocalDate.of(2026, 10, 3))
                .applyUrl("https://example.com/apply")
                .build();
    }

    private CardNews card(Policy policy, Long cardNo, String title, String body) {
        return CardNews.builder()
                .policy(policy)
                .cardNo(cardNo)
                .title(title)
                .body(body)
                .build();
    }
}
