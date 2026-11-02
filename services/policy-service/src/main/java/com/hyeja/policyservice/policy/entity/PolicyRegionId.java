package com.hyeja.policyservice.policy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serial;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Embeddable
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PolicyRegionId implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Column(name = "policy_id", nullable = false, length = 30)
    private String policyId;

    @Column(name = "region_code", nullable = false, length = 5, columnDefinition = "CHAR(5)")
    private String regionCode;

    public PolicyRegionId(String policyId, String regionCode) {
        this.policyId = policyId;
        this.regionCode = regionCode;
    }
}
