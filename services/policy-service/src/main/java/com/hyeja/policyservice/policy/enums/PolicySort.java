package com.hyeja.policyservice.policy.enums;

import org.springframework.data.domain.Sort;

public enum PolicySort {

    DEADLINE(Sort.by(
            Sort.Order.asc("applyEndDate").nullsLast(),
            Sort.Order.asc("policyId")
    )),
    VIEW_COUNT(Sort.by(
            Sort.Order.desc("viewCount"),
            Sort.Order.asc("policyId")
    )),
    NAME(Sort.by(
            Sort.Order.asc("policyName"),
            Sort.Order.asc("policyId")
    ));

    private final Sort sort;

    PolicySort(Sort sort) {
        this.sort = sort;
    }

    public Sort toSort() {
        return sort;
    }
}
