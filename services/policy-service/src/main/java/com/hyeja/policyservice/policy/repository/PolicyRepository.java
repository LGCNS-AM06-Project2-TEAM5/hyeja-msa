package com.hyeja.policyservice.policy.repository;

import com.hyeja.policyservice.policy.entity.Policy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
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


    @Query("""
            select policy
            from Policy policy
            where policy.deletedAt is null
              and policy.activeYn = true
              and policy.applyPeriodCode <> com.hyeja.policyservice.policy.enums.PolicyApplyPeriod.CLOSED
              and (policy.applyStartDate is null or policy.applyStartDate <= :today)
              and (policy.applyEndDate is null or policy.applyEndDate >= :today)
              and (
                  (:monthlyRent = true and locate(',MONTHLY_RENT,',
                      concat(',', concat(cast(policy.categories as string), ','))) > 0)
                  or (:jeonse = true and locate(',JEONSE,',
                      concat(',', concat(cast(policy.categories as string), ','))) > 0)
                  or (:purchase = true and locate(',PURCHASE,',
                      concat(',', concat(cast(policy.categories as string), ','))) > 0)
                  or (:publicRent = true and locate(',PUBLIC_RENT,',
                      concat(',', concat(cast(policy.categories as string), ','))) > 0)
                  or (:other = true and locate(',OTHER,',
                      concat(',', concat(cast(policy.categories as string), ','))) > 0)
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
                        and (
                            matchingRegion.region.regionCode = :regionCode
                            or (
                                matchingRegion.region.regionCode like '%000'
                                and substring(matchingRegion.region.regionCode, 1, 2)
                                    = substring(:regionCode, 1, 2)
                            )
                        )
                  )
              )
            """)
    List<Policy> searchActivePolicies(
            @Param("monthlyRent") boolean monthlyRent,
            @Param("jeonse") boolean jeonse,
            @Param("purchase") boolean purchase,
            @Param("publicRent") boolean publicRent,
            @Param("other") boolean other,
            @Param("regionCode") String regionCode,
            @Param("today") LocalDate today,
            Pageable pageable
    );
}
