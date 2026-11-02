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

}
