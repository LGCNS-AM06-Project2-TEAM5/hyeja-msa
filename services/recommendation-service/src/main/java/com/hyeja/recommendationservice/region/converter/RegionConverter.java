package com.hyeja.recommendationservice.region.converter;

import com.hyeja.recommendationservice.region.dto.RegionDTO;
import java.util.List;

public class RegionConverter {

    /**
     * 정책 대상 지역을 한 줄로 요약합니다. 없으면 "전국", 한 곳이면 그 이름, 여러 곳이면 "서울특별시 종로구 외 24곳".
     * 1차 RegionConverter.summarize와 같은 문구이며, policy-service의 정책 목록·상세·카드뉴스 문구와 같아야 합니다.
     */
    public static String summarize(List<RegionDTO> regions) {
        if (regions.isEmpty()) {
            return "전국";
        }
        String first = regions.get(0).getSigunguName();
        return regions.size() == 1 ? first : "%s 외 %d곳".formatted(first, regions.size() - 1);
    }
}
