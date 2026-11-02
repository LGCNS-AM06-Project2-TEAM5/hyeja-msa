package com.hyeja.policyservice.region.service;

import com.hyeja.policyservice.region.converter.RegionConverter;
import com.hyeja.policyservice.region.dto.RegionResponseDTO.SidoDTO;
import com.hyeja.policyservice.region.entity.Region;
import com.hyeja.policyservice.region.repository.RegionRepository;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RegionService {

    private final RegionRepository regionRepository;

    /**
     * 전체 지역(시군구 256건)을 코드 순으로 읽어 시·도별로 묶어 돌려줍니다.
     * 건수가 작아 필터 없이 한 번에 내려주고, 프론트가 드롭다운에서 시·도를 골라 거릅니다.
     */
    public List<SidoDTO> getRegions() {
        List<Region> regions = regionRepository.findAll(Sort.by("regionCode"));
        // 구가 있는 시의 상위 시(예: "경기도 수원시")는 뺍니다. 정책 지역은 구 코드로만 저장돼 상위 시를 고르면 어떤 정책과도 맞지 않습니다.
        // CSV에서는 이미 뺐지만, 회원 조건이 참조 중이라 DB에 남은 행도 목록에 나오지 않게 합니다.
        // 상위 시 이름 = 구 이름에서 마지막 단어를 뺀 것 ("경기도 수원시 장안구" → "경기도 수원시")
        Set<String> parentCityNames = regions.stream()
                .map(Region::getSigunguName)
                .filter(name -> name.split(" ").length >= 3)
                .map(name -> name.substring(0, name.lastIndexOf(' ')))
                .collect(Collectors.toSet());
        return RegionConverter.toSidoDTOs(regions.stream()
                .filter(region -> !parentCityNames.contains(region.getSigunguName()))
                .toList());
    }
}
