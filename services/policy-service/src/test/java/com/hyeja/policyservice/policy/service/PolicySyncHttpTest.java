package com.hyeja.policyservice.policy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.hyeja.policyservice.global.config.RestTemplateConfig;
import com.hyeja.policyservice.policy.converter.PolicyApiCodeConverter;
import com.hyeja.policyservice.policy.dto.PolicyApiResponseDTO.PolicyItem;
import com.hyeja.policyservice.policy.repository.PolicyRepository;
import com.hyeja.policyservice.policy.repository.PolicyRegionRepository;
import com.hyeja.policyservice.term.repository.TermRepository;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;

class PolicySyncHttpTest {
    @Test
    void requestsOriginalParametersAndDeserializesYouthResponse() {
        var template = new RestTemplateConfig().restTemplate(new org.springframework.boot.restclient.RestTemplateBuilder());
        var server = MockRestServiceServer.bindTo(template).build();
        var analyzer = mock(PolicyAiAnalyzer.class);
        var saver = mock(PolicySyncItemService.class);
        var analysis = mock(PolicyAiAnalysis.class);
        when(analyzer.analyze(any())).thenReturn(analysis);
        var service = new PolicyService(template, new PolicyApiCodeConverter(), analyzer, saver,
                mock(PolicyRepository.class), mock(PolicyRegionRepository.class),
                mock(TermRepository.class), Clock.systemUTC(), mock(PolicyMemberService.class));
        ReflectionTestUtils.setField(service, "apiKey", "test-key");
        ReflectionTestUtils.setField(service, "apiUrl", "https://example.com/policies");
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://example.com/policies?")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("apiKeyNm", "test-key"))
                .andExpect(queryParam("pageNum", "1"))
                .andExpect(queryParam("pageSize", "100"))
                .andExpect(queryParam("rtnType", "json"))
                .andRespond(withSuccess("""
                        {"resultCode":200,"result":{
                          "pagging":{"totCount":1,"pageNum":1,"pageSize":100},
                          "youthPolicyList":[{
                            "plcyNo":"00123","plcyNm":"주거 정책","lclsfNm":"주거",
                            "plcyAprvSttsCd":"0044002","unknown":"추가 필드"
                          }]
                        }}
                        """, MediaType.APPLICATION_JSON));

        assertThat(service.fetchAndSaveHousingPolicies()).isEqualTo(1);
        var item = org.mockito.ArgumentCaptor.forClass(PolicyItem.class);
        verify(saver).save(item.capture(), same(analysis));
        assertThat(item.getValue().getPolicyId()).isEqualTo("00123");
        assertThat(item.getValue().getPolicyName()).isEqualTo("주거 정책");
        server.verify();
    }
}
