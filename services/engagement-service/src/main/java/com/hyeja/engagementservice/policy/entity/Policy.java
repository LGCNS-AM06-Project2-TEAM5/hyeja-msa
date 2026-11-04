package com.hyeja.engagementservice.policy.entity;

import com.hyeja.engagementservice.global.baseEntity.BaseEntity;
import com.hyeja.engagementservice.policy.converter.PolicyCategoryConverter;
import com.hyeja.engagementservice.policy.enums.*;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.Set;
import lombok.*;

/** Engagement-owned read model; never maps the policy-service database. */
@Entity
@Table(name = "policy_snapshot")
@Getter
@Setter
@NoArgsConstructor
public class Policy extends BaseEntity {
    @Id @Column(length = 100) private String policyId;
    @Column(nullable = false, length = 200) private String policyName;
    @Convert(converter = PolicyCategoryConverter.class) private Set<PolicyCategory> categories;
    @Column(columnDefinition = "TEXT") private String supportContent;
    private LocalDate applyEndDate;
    @Enumerated(EnumType.STRING) private PolicyApplyPeriod applyPeriodCode;
    @Column(length = 500) private String applyUrl;
    private Boolean activeYn;
}
