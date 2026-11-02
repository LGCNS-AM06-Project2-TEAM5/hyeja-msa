package com.hyeja.userservice.domain.region.init;

import static org.assertj.core.api.Assertions.assertThat;

import com.hyeja.userservice.domain.region.repository.RegionRepository;
import com.hyeja.userservice.global.config.JpaAuditingConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@Import({JpaAuditingConfig.class, RegionDataInitializer.class})
class RegionDataInitializerTest {

    @Autowired
    private RegionDataInitializer regionDataInitializer;

    @Autowired
    private RegionRepository regionRepository;

    @Test
    @DisplayName("CSV의 시군구 256건을 적재하고, 다시 실행해도 중복 저장하지 않는다")
    void loadRegions() throws Exception {
        // 서버를 두 번 켠 상황
        regionDataInitializer.run();
        regionDataInitializer.run();
        regionRepository.flush();

        assertThat(regionRepository.count()).isEqualTo(256);
        assertThat(regionRepository.findById("11110"))
                .hasValueSatisfying(region -> assertThat(region.getSigunguName()).isEqualTo("서울특별시 종로구"));
        // 구가 있는 시의 상위 시(수원시)는 제외됨
        assertThat(regionRepository.existsById("41110")).isFalse();
        // 시·도 앞 두 자리로 시군구 목록 조회
        assertThat(regionRepository.findAllByRegionCodeStartingWith("41")).isNotEmpty();
    }
}
