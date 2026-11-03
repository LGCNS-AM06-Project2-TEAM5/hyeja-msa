package com.hyeja.gateway.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import org.junit.jupiter.api.Test;

class TracePrivacyConfigurationTest {
    @Test
    void removesHttpUriWithoutRemovingTheRouteTemplate() {
        var context = new Observation.Context();
        context.addHighCardinalityKeyValue(KeyValue.of("http.uri", "/api/members?code=secret"));
        context.addLowCardinalityKeyValue(KeyValue.of("uri", "/api/members"));

        var filtered = new TracePrivacyConfiguration().redactHttpUrls().map(context);

        assertThat(filtered.getHighCardinalityKeyValue("http.uri")).isNull();
        assertThat(filtered.getLowCardinalityKeyValue("http.uri")).isNull();
        assertThat(filtered.getLowCardinalityKeyValue("uri").getValue()).isEqualTo("/api/members");
    }
}
