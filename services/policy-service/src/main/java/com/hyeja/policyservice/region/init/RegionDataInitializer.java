package com.hyeja.policyservice.region.init;

import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import com.hyeja.policyservice.region.dto.RegionCsvDto;
import com.hyeja.policyservice.region.entity.Region;
import com.hyeja.policyservice.region.repository.RegionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class RegionDataInitializer implements CommandLineRunner {

    private final RegionRepository regionRepository;
    private final Environment environment; // 환경 정보를 읽기 위한 주입
    
    @Override
    public void run(String... args) throws Exception {
        // [중요] 테스트 환경("test")인 경우 CSV 로드 및 초기화 로직을 실행하지 않음
        List<String> activeProfiles = Arrays.asList(environment.getActiveProfiles());
        if (activeProfiles.contains("test")) {
            log.info("[RegionDataInitializer] Test environment detected. Skipping CSV initialization.");
            return;
        }
        log.info("[RegionDataInitializer] Starting region CSV preprocessing and upload...");

        // 시군구 256건(구가 있는 시의 상위 시 제외). 출처·가공 방법·갱신 방법은 CSV 파일 맨 위 # 주석에 적어 두었습니다.
        ClassPathResource resource = new ClassPathResource("csv/region_sigungu.csv");

        try (InputStream inputStream = resource.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            
            CsvMapper csvMapper = new CsvMapper();
            CsvSchema csvSchema = CsvSchema.emptySchema()
                    .withHeader()
                    .withComments() // # 으로 시작하는 줄(출처 설명)은 건너뜁니다.
                    .withColumnReordering(true);

            MappingIterator<RegionCsvDto> mappingIterator = csvMapper
                    .readerFor(RegionCsvDto.class)
                    .with(csvSchema)
                    .readValues(reader);

            List<RegionCsvDto> csvDataList = mappingIterator.readAll();

            // 1. 이미 DB에 있는 코드는 갱신하고 없는 코드는 추가하여,
            //    서버를 다시 켜도 중복 저장되지 않습니다.
            Map<String, Region> existingRegions = regionRepository.findAll().stream()
                    .collect(Collectors.toMap(Region::getRegionCode, Function.identity()));

            // 2. CSV 값을 그대로 저장합니다. 예: 41111 / "경기도 수원시 장안구"
            List<Region> regions = csvDataList.stream()
                    .map(dto -> {
                        String regionCode = dto.getRegionCode().trim();
                        String sigunguName = dto.getSigunguName().trim();
                        Region existingRegion = existingRegions.get(regionCode);
                        if (existingRegion != null) {
                            existingRegion.updateSigunguName(sigunguName);
                            return existingRegion;
                        }
                        return Region.builder()
                                .regionCode(regionCode)
                                .sigunguName(sigunguName)
                                .build();
                    })
                    .toList();

            if (regions.isEmpty()) {
                log.info("[RegionDataInitializer] Region CSV is empty.");
                return;
            }

            // 3. 코드가 있으면 이름을 갱신하고, 없으면 새 지역을 추가합니다.
            regionRepository.saveAll(regions);
            log.info("[RegionDataInitializer] Synchronized {} regions.", regions.size());

        } catch (Exception e) {
            log.error("[RegionDataInitializer] Failed to upload region CSV data", e);
        }
    }
}
