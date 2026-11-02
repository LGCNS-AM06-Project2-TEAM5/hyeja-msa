package com.hyeja.policyservice.policy.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class PolicyApiResponseDTOTest {

    @Test
    void readsYouthApiFieldNamesAndIgnoresUnknownFields() {
        String json = """
                {
                  "resultCode": 200,
                  "resultMessage": "성공",
                  "unknownRoot": true,
                  "result": {
                    "unknownResult": "추가 필드",
                    "pagging": {"totCount": 275, "pageNum": 1, "pageSize": 100, "extra": 0},
                    "youthPolicyList": [{
                      "plcyNo": "00001234", "plcyNm": "청년 월세 지원",
                      "lclsfNm": "주거,주거", "mclsfNm": "임차료 지원",
                      "plcyKywdNm": "청년,월세", "plcyExplnCn": "정책 설명",
                      "plcySprtCn": "월세 지원", "sprtTrgtMinAge": "19",
                      "sprtTrgtMaxAge": "34", "sprtTrgtAgeLmtYn": "Y",
                      "earnCndSeCd": "0043002", "earnMinAmt": "100",
                      "earnMaxAmt": "300", "earnEtcCn": "소득 조건",
                      "mrgSttsCd": "0055001", "jobCd": "0013001,0013002",
                      "zipCd": "01234,11440", "aplyPrdSeCd": "0057001",
                      "addAplyQlfcCndCn": "추가 자격", "ptcpPrpTrgtCn": "청년",
                      "aplyYmd": "20260901 ~ 20260930", "plcyAplyMthdCn": "온라인",
                      "aplyUrlAddr": "https://example.com/apply",
                      "refUrlAddr1": "https://example.com/1",
                      "refUrlAddr2": "https://example.com/2",
                      "inqCnt": "123", "plcyAprvSttsCd": "0044002", "unknownItem": true
                    }]
                  }
                }
                """;

        PolicyApiResponseDTO response = JsonMapper.builder().build()
                .readValue(json, PolicyApiResponseDTO.class);

        assertThat(response.getResultCode()).isEqualTo(200);
        assertThat(response.getResultMessage()).isEqualTo("성공");
        var result = response.getResult();
        assertThat(result.getPagging().getTotCount()).isEqualTo(275);
        assertThat(result.getPagging().getPageNum()).isEqualTo(1);
        assertThat(result.getPagging().getPageSize()).isEqualTo(100);
        assertThat(result.getYouthPolicyList()).singleElement().satisfies(item -> {
            assertThat(item.getPolicyId()).isEqualTo("00001234");
            assertThat(item.getPolicyName()).isEqualTo("청년 월세 지원");
            assertThat(item.getCategory()).isEqualTo("주거,주거");
            assertThat(item.getSubCategory()).isEqualTo("임차료 지원");
            assertThat(item.getKeywords()).isEqualTo("청년,월세");
            assertThat(item.getPolicyExplanation()).isEqualTo("정책 설명");
            assertThat(item.getSupportContent()).isEqualTo("월세 지원");
            assertThat(item.getMinAge()).isEqualTo("19");
            assertThat(item.getMaxAge()).isEqualTo("34");
            assertThat(item.getAgeLimitYn()).isEqualTo("Y");
            assertThat(item.getIncomeConditionCode()).isEqualTo("0043002");
            assertThat(item.getIncomeMin()).isEqualTo("100");
            assertThat(item.getIncomeMax()).isEqualTo("300");
            assertThat(item.getIncomeEtc()).isEqualTo("소득 조건");
            assertThat(item.getMarriageCode()).isEqualTo("0055001");
            assertThat(item.getEmploymentCodes()).isEqualTo("0013001,0013002");
            assertThat(item.getRegionCodes()).isEqualTo("01234,11440");
            assertThat(item.getApplyPeriodCode()).isEqualTo("0057001");
            assertThat(item.getExtraQualification()).isEqualTo("추가 자격");
            assertThat(item.getParticipantTarget()).isEqualTo("청년");
            assertThat(item.getApplyYmd()).isEqualTo("20260901 ~ 20260930");
            assertThat(item.getApplyMethod()).isEqualTo("온라인");
            assertThat(item.getApplyUrl()).isEqualTo("https://example.com/apply");
            assertThat(item.getReferenceUrl1()).isEqualTo("https://example.com/1");
            assertThat(item.getReferenceUrl2()).isEqualTo("https://example.com/2");
            assertThat(item.getViewCount()).isEqualTo("123");
            assertThat(item.getApprovalStatusCode()).isEqualTo("0044002");
        });
    }
}
