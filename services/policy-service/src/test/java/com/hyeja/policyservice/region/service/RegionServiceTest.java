package com.hyeja.policyservice.region.service;

import com.hyeja.policyservice.region.dto.RegionResponseDTO.SidoDTO;
import com.hyeja.policyservice.region.dto.RegionResponseDTO.SigunguDTO;
import com.hyeja.policyservice.region.entity.Region;
import com.hyeja.policyservice.region.init.RegionDataInitializer;
import com.hyeja.policyservice.region.repository.RegionRepository;
import com.hyeja.policyservice.global.config.JpaAuditingConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

// 실제 지역 CSV(256건)를 적재한 뒤 시·도별로 묶이는지 검증합니다.
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
class RegionServiceTest {

    @Autowired
    private RegionRepository regionRepository;

    @Test
    void groupsAllRegionsBySido() throws Exception {
        new RegionDataInitializer(regionRepository, new MockEnvironment()).run();
        // 회원 조건이 참조 중이라 정리되지 않고 DB에 남은 상위 시 행도 목록에는 나오지 않아야 합니다.
        regionRepository.save(Region.builder().regionCode("41110").sigunguName("경기도 수원시").build());

        List<SidoDTO> result = new RegionService(regionRepository).getRegions();

        // 시·도 16개, 시·군·구 256건이 코드 순으로 묶입니다.
        assertThat(result).hasSize(16);
        assertThat(result).extracting(SidoDTO::getSidoCode).isSorted();
        assertThat(result.stream().mapToInt(sido -> sido.getSigungu().size()).sum()).isEqualTo(256);

        SidoDTO seoul = result.get(0);
        assertThat(seoul.getSidoCode()).isEqualTo("11");
        assertThat(seoul.getSidoName()).isEqualTo("서울특별시");
        // 시·군·구 이름에서 시·도 이름이 빠집니다.
        assertThat(seoul.getSigungu()).extracting(SigunguDTO::getRegionCode, SigunguDTO::getSigunguName)
                .contains(tuple("11440", "마포구"));

        // 상위 시(수원시)는 빠지고 구(장안구 등)만 남습니다.
        SidoDTO gyeonggi = result.stream().filter(sido -> sido.getSidoCode().equals("41")).findFirst().orElseThrow();
        assertThat(gyeonggi.getSigungu()).extracting(SigunguDTO::getRegionCode)
                .doesNotContain("41110").contains("41111");

        // 세종은 시·도와 이름이 같아 그대로 둡니다.
        SidoDTO sejong = result.stream().filter(sido -> sido.getSidoCode().equals("36")).findFirst().orElseThrow();
        assertThat(sejong.getSigungu()).extracting(SigunguDTO::getSigunguName).containsExactly("세종특별자치시");

        // 모든 시·도에서 시·군·구 이름에 시·도 이름이 남아 있지 않아야 합니다 (데이터 형식 검증).
        result.stream().filter(sido -> !sido.getSidoCode().equals("36")).forEach(sido ->
                assertThat(sido.getSigungu()).extracting(SigunguDTO::getSigunguName)
                        .noneMatch(name -> name.startsWith(sido.getSidoName())));
    }
}
