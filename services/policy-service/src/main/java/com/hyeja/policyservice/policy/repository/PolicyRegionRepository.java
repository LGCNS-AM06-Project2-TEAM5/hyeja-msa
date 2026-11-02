package com.hyeja.policyservice.policy.repository;

import com.hyeja.policyservice.policy.entity.PolicyRegion;
import com.hyeja.policyservice.policy.entity.PolicyRegionId;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PolicyRegionRepository extends JpaRepository<PolicyRegion, PolicyRegionId> {

    @Query("""
            select policyRegion
            from PolicyRegion policyRegion
            join fetch policyRegion.region region
            where policyRegion.policy.policyId in :policyIds
              and policyRegion.deletedAt is null
              and region.deletedAt is null
            order by policyRegion.policy.policyId, region.regionCode
            """)
    List<PolicyRegion> findAllActiveByPolicyIds(
            @Param("policyIds") Collection<String> policyIds
    );

    List<PolicyRegion> findAllByPolicy_PolicyId(String policyId);

    @Modifying
    @Query("delete from PolicyRegion policyRegion "
            + "where policyRegion.policy.policyId = :policyId")
    void deleteAllByPolicy_PolicyId(@Param("policyId") String policyId);
}
