package com.hyeja.policyservice.region.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties (ignoreUnknown = true)
public class RegionCsvDto {
    private String regionCode;
    private String sigunguName;
}