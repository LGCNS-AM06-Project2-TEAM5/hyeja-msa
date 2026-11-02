package com.hyeja.policyservice.policy.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class PolicyApiResponseDTO {
    private int resultCode;
    private String resultMessage;
    private ResultData result;

    @Getter @Setter @ToString
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ResultData {
        private Pagging pagging;
        @JsonProperty("youthPolicyList") private List<PolicyItem> youthPolicyList;
    }

    @Getter @Setter @ToString
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Pagging {
        private int totCount;
        private int pageNum;
        private int pageSize;
    }

    @Getter @Setter @ToString
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PolicyItem {
        @JsonProperty("plcyNo") private String policyId;
        @JsonProperty("plcyNm") private String policyName;
        @JsonProperty("lclsfNm") private String category; // ai
        @JsonProperty("mclsfNm") private String subCategory;
        @JsonProperty("plcyKywdNm") private String keywords;
        @JsonProperty("plcyExplnCn") private String policyExplanation;
        @JsonProperty("plcySprtCn") private String supportContent;
        @JsonProperty("sprtTrgtMinAge") private String minAge;
        @JsonProperty("sprtTrgtMaxAge") private String maxAge;
        @JsonProperty("sprtTrgtAgeLmtYn") private String ageLimitYn;
        @JsonProperty("earnCndSeCd") private String incomeConditionCode;
        @JsonProperty("earnMinAmt") private String incomeMin;
        @JsonProperty("earnMaxAmt") private String incomeMax;
        @JsonProperty("earnEtcCn") private String incomeEtc;
        @JsonProperty("mrgSttsCd") private String marriageCode;
        @JsonProperty("jobCd") private String employmentCodes;
        @JsonProperty("zipCd") private String regionCodes;
        @JsonProperty("aplyPrdSeCd") private String applyPeriodCode;
        @JsonProperty("addAplyQlfcCndCn") private String extraQualification;
        @JsonProperty("ptcpPrpTrgtCn") private String participantTarget;
        @JsonProperty("aplyYmd") private String applyYmd;
        @JsonProperty("plcyAplyMthdCn") private String applyMethod;
        @JsonProperty("aplyUrlAddr") private String applyUrl;
        @JsonProperty("refUrlAddr1") private String referenceUrl1;
        @JsonProperty("refUrlAddr2") private String referenceUrl2;
        @JsonProperty("inqCnt") private String viewCount;
        @JsonProperty("plcyAprvSttsCd") private String approvalStatusCode;
    }
}

