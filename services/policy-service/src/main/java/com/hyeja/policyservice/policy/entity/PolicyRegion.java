package com.hyeja.policyservice.policy.entity;

import com.hyeja.policyservice.region.entity.Region;
import com.hyeja.policyservice.global.baseEntity.BaseEntity;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "policy_region")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PolicyRegion extends BaseEntity {

    @EmbeddedId
    private PolicyRegionId id;

    @MapsId("policyId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "policy_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_policy_region_policy"))
    private Policy policy;

    @MapsId("regionCode")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "region_code", nullable = false,
            foreignKey = @ForeignKey(name = "fk_policy_region_region"))
    private Region region;

    // 공유하는 정책과 지역에는 저장·삭제를 전파하지 않습니다.
    @Builder
    public PolicyRegion(Policy policy, Region region) {
        this.policy = Objects.requireNonNull(policy, "정책은 필수입니다.");
        this.region = Objects.requireNonNull(region, "지역은 필수입니다.");
        this.id = new PolicyRegionId(
                Objects.requireNonNull(policy.getPolicyId(), "정책 ID는 필수입니다."),
                Objects.requireNonNull(region.getRegionCode(), "지역 코드는 필수입니다."));
    }
}
