package com.hyeja.policyservice.region.init;

import com.hyeja.policyservice.region.entity.Region;
import com.hyeja.policyservice.region.repository.RegionRepository;
import com.hyeja.policyservice.global.config.JpaAuditingConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

// 실제 CSV로 적재 규칙을 검증합니다. 초기화 코드는 test 프로필에서 건너뛰므로, 프로필 없는 환경을 넣어 직접 실행합니다.
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
class RegionDataInitializerTest {

    // csv/region_sigungu.csv 행 수 (시군구 256건, 구가 있는 시의 상위 시 제외)
    private static final long SIGUNGU_COUNT = 256;

    @Autowired
    private RegionRepository regionRepository;

    @Test
    void loadsAllSigunguEvenWhenSeedExists() throws Exception {
        // 일부 지역이 이미 들어가 있는 상태
        regionRepository.save(Region.builder()
                .regionCode("11440").sigunguName("변경 전 이름").build());

        initializer().run();

        assertThat(regionRepository.count()).isEqualTo(SIGUNGU_COUNT);
        assertThat(regionRepository.findById("11440")).get()
                .extracting(Region::getSigunguName).isEqualTo("서울특별시 마포구");
        // 구 이름까지 온전히 저장 (예전 국토교통부 CSV에서는 "경기도 수원시"로 잘렸음)
        assertThat(regionRepository.findById("41111")).get()
                .extracting(Region::getSigunguName).isEqualTo("경기도 수원시 장안구");
        // 최근 행정구역 변경 반영 (광주·전남 통합, 인천 구 개편)
        assertThat(regionRepository.findById("12110")).get()
                .extracting(Region::getSigunguName).isEqualTo("전남광주통합특별시 목포시");
        assertThat(regionRepository.findById("28125")).get()
                .extracting(Region::getSigunguName).isEqualTo("인천광역시 제물포구");
    }

    @Test
    void doesNotAddDuplicatesWhenRunAgain() throws Exception {
        initializer().run();
        initializer().run();

        assertThat(regionRepository.count()).isEqualTo(SIGUNGU_COUNT);
    }

    private RegionDataInitializer initializer() {
        return new RegionDataInitializer(regionRepository, new MockEnvironment());
    }
}
