package com.hyeja.policyservice.internal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.hyeja.policyservice.internal.dto.PolicyCandidateRequest;
import com.hyeja.policyservice.internal.service.PolicyCandidateService;
import com.hyeja.policyservice.policy.repository.PolicyRepository;
import com.hyeja.policyservice.policy.repository.PolicyRegionRepository;
import com.hyeja.policyservice.policy.enums.PolicyCategory;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.mockito.ArgumentCaptor;

class PolicyCandidateServiceTest {
    private final PolicyRepository policies = mock(PolicyRepository.class);
    private final PolicyRegionRepository regions = mock(PolicyRegionRepository.class);
    private final PolicyCandidateService service = new PolicyCandidateService(policies, regions);
    private final LocalDate today = LocalDate.of(2026, 11, 2);

    // 원본 PolicySearchServiceTest의 빈 후보 성공 동작을 F2 경계로 이관했습니다.
    @Test
    void returnsEmptySuccessResultWhenNoCandidateExists() {
        when(policies.searchActivePolicies(eq(false), eq(true), eq(false), eq(false), eq(false),
                eq("11440"), eq(today), any(Pageable.class))).thenReturn(List.of());
        assertThat(service.findCandidates(new PolicyCandidateRequest(
                List.of(PolicyCategory.JEONSE), "11440", today))).isEmpty();
        verifyNoInteractions(regions);
    }

    // 원본의 카테고리 미지정 시 전체 조회 규칙. F2에서는 빈 배열로 전달합니다.
    @Test
    void searchesAllCategoriesWhenNoSpecificCategoryIsRequested() {
        when(policies.searchActivePolicies(anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(),
                anyBoolean(), anyString(), any(), any())).thenReturn(List.of());
        service.findCandidates(new PolicyCandidateRequest(List.of(), "11440", today));
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(policies).searchActivePolicies(eq(true), eq(true), eq(true), eq(true), eq(true),
                eq("11440"), eq(today), page.capture());
        assertThat(page.getValue().getPageSize()).isEqualTo(20);
        assertThat(page.getValue().getPageNumber()).isZero();
    }

    @Test
    void propagatesRepositoryFailureInsteadOfReturningEmptyCandidates() {
        when(policies.searchActivePolicies(anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(),
                anyBoolean(), anyString(), any(), any())).thenThrow(new IllegalStateException("DB unavailable"));
        assertThatThrownBy(() -> service.findCandidates(new PolicyCandidateRequest(List.of(), "11440", today)))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(regions);
    }

}
