package com.hyeja.policyservice.cardnews.repository;

import com.hyeja.policyservice.cardnews.entity.CardNews;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository 
public interface CardNewsRepository extends JpaRepository<CardNews, Long> {

    // LIMIT 4를 포함하여 대표 카드(card_no = 1)를 마감일 오름차순으로 상위 4개 조회
    @Query("""
            select cardNews from CardNews cardNews join fetch cardNews.policy policy
            where cardNews.cardNo = 1 and cardNews.deletedAt is null
              and policy.deletedAt is null and policy.activeYn = true
              and policy.applyPeriodCode <> com.hyeja.policyservice.policy.enums.PolicyApplyPeriod.CLOSED
              and (policy.applyEndDate is null or policy.applyEndDate >= :today)
            order by case when policy.applyEndDate is null then 1 else 0 end,
                     policy.applyEndDate, policy.policyId
            """)
    List<CardNews> findGuestHomeCardNews(@Param("today") java.time.LocalDate today, Pageable pageable);

    @Query("""
            select cardNews from CardNews cardNews join fetch cardNews.policy policy
            where cardNews.cardNo = 1 and cardNews.deletedAt is null
              and policy.deletedAt is null and policy.activeYn = true
              and policy.applyPeriodCode <> com.hyeja.policyservice.policy.enums.PolicyApplyPeriod.CLOSED
              and (policy.applyEndDate is null or policy.applyEndDate >= :today)
              and (not exists (
                    select policyRegion.id from PolicyRegion policyRegion
                    where policyRegion.policy = policy and policyRegion.deletedAt is null
                      and policyRegion.region.deletedAt is null)
                   or exists (
                    select matchingRegion.id from PolicyRegion matchingRegion
                    where matchingRegion.policy = policy and matchingRegion.deletedAt is null
                      and matchingRegion.region.deletedAt is null
                      and matchingRegion.region.regionCode in (:regionCode, :sidoCode)))
            order by case when policy.applyEndDate is null then 1 else 0 end,
                     policy.applyEndDate, policy.viewCount desc, policy.policyName, policy.policyId
            """)
    List<CardNews> findMemberHomeCardNews(
            @Param("regionCode") String regionCode,
            @Param("sidoCode") String sidoCode,
            @Param("today") java.time.LocalDate today,
            Pageable pageable);

    // 특정 정책 ID와 카드 번호(card_No)로 카드뉴스가 이미 존재하는지 확인하는 메서드
    boolean existsByPolicy_PolicyIdAndCardNo(String policyId, Long cardNo);

    @Query("""
            select cardNews
            from CardNews cardNews
            join fetch cardNews.policy policy
            where policy.policyId = :policyId
              and cardNews.deletedAt is null
              and policy.deletedAt is null
              and policy.activeYn = true
            order by cardNews.cardNo
            """)
    List<CardNews> findAllActiveByPolicyIdOrderByCardNo(@Param("policyId") String policyId);
}
