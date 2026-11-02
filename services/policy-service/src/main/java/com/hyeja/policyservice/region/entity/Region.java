package com.hyeja.policyservice.region.entity;

import com.hyeja.policyservice.global.baseEntity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "region")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Region extends BaseEntity {

    @Id
    @Column(name = "region_code", nullable = false, length = 5,
            columnDefinition = "CHAR(5)", updatable = false)
    private String regionCode;

    @Column(name = "sigungu_name", nullable = false, length = 30)
    private String sigunguName;

    @Builder
    public Region(String regionCode, String sigunguName) {
        this.regionCode = regionCode;
        this.sigunguName = sigunguName;
    }

    public void updateSigunguName(String sigunguName) {
        this.sigunguName = sigunguName;
    }
}
