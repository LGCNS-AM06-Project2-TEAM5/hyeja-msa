package com.hyeja.policyservice.policy.repository;

import com.hyeja.policyservice.policy.entity.Policy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface PolicyRepository extends JpaRepository<Policy, String> {

    @Query("""
            select policy
            from Policy policy
            where policy.deletedAt is null
              and policy.activeYn = true
              and policy.applyPeriodCode <> com.hyeja.policyservice.policy.enums.PolicyApplyPeriod.CLOSED
              and (policy.applyStartDate is null or policy.applyStartDate <= :today)
              and (policy.applyEndDate is null or policy.applyEndDate >= :today)
              and (
                  :category is null
                  or locate(
                      concat(',', concat(:category, ',')),
                      concat(',', concat(cast(policy.categories as string), ','))
                  ) > 0
              )
            """)
    Page<Policy> findGuestHousingPolicies(
            @Param("category") String category,
            @Param("today") LocalDate today,
            Pageable pageable
    );

    // 로그인 회원 목록(/housing/me, PolicyMemberService). 1차 PolicyRepository.findHousingPoliciesForMember 그대로입니다.
    @Query("""
            select policy
            from Policy policy
            where policy.deletedAt is null
              and policy.activeYn = true
              and policy.applyPeriodCode <> com.hyeja.policyservice.policy.enums.PolicyApplyPeriod.CLOSED
              and (policy.applyStartDate is null or policy.applyStartDate <= :today)
              and (policy.applyEndDate is null or policy.applyEndDate >= :today)
              and (
                  :category is null
                  or locate(
                      concat(',', concat(:category, ',')),
                      concat(',', concat(cast(policy.categories as string), ','))
                  ) > 0
              )
              and (
                  :onlyEligible = false
                  or (
                      (
                          policy.ageLimitYn = false
                          or (
                              (policy.minAge is null or policy.minAge <= :age)
                              and (policy.maxAge is null or policy.maxAge >= :age)
                          )
                      )
                      and (
                          policy.houselessRequirement is null
                          or policy.houselessRequirement = com.hyeja.policyservice.policy.enums.PolicyHouselessRequirement.NOT_REQUIRED
                          or policy.houselessRequirement = com.hyeja.policyservice.policy.enums.PolicyHouselessRequirement.UNKNOWN
                          or :houselessYn = true
                      )
                      and (
                          policy.employmentCodes is null
                          or trim(cast(policy.employmentCodes as string)) = ''
                          or locate(
                              ',NO_RESTRICTION,',
                              concat(',', concat(cast(policy.employmentCodes as string), ','))
                          ) > 0
                          or locate(
                              concat(',', concat(:employmentCode, ',')),
                              concat(',', concat(cast(policy.employmentCodes as string), ','))
                          ) > 0
                      )
                      and (
                          not exists (
                              select policyRegion.id
                              from PolicyRegion policyRegion
                              where policyRegion.policy = policy
                                and policyRegion.deletedAt is null
                                and policyRegion.region.deletedAt is null
                          )
                          or exists (
                              select matchingRegion.id
                              from PolicyRegion matchingRegion
                              where matchingRegion.policy = policy
                                and matchingRegion.deletedAt is null
                                and matchingRegion.region.deletedAt is null
                                and matchingRegion.region.regionCode = :regionCode
                          )
                      )
                  )
              )
            """)
    Page<Policy> findHousingPoliciesForMember(
            @Param("category") String category,
            @Param("onlyEligible") boolean onlyEligible,
            @Param("today") LocalDate today,
            @Param("age") int age,
            @Param("houselessYn") boolean houselessYn,
            @Param("employmentCode") String employmentCode,
            @Param("regionCode") String regionCode,
            Pageable pageable
    );

}
