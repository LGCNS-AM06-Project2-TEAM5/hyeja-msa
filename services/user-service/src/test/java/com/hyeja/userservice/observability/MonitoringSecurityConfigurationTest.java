package com.hyeja.userservice.observability;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = MonitoringSecurityConfigurationTest.TestConfiguration.class)
@TestPropertySource(properties = "MONITORING_PASSWORD=test-monitoring-password-1234")
class MonitoringSecurityConfigurationTest {
    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    @EnableWebSecurity
    @EnableWebMvc
    @Import({MonitoringSecurityConfiguration.class, Routes.class,
            com.hyeja.userservice.global.config.PasswordEncoderConfig.class})
    static class TestConfiguration {}

    @RestController
    @org.springframework.boot.test.context.TestComponent
    static class Routes {
        @GetMapping("/actuator/prometheus")
        String metrics() { return "test_metric 1"; }
    }

    @Autowired WebApplicationContext context;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void onlyMonitoringCredentialsCanReadMetrics() throws Exception {
        mvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/prometheus").with(httpBasic("monitoring", "wrong")))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/prometheus").with(httpBasic("monitoring", "test-monitoring-password-1234")))
                .andExpect(status().isOk());
        mvc.perform(post("/actuator/prometheus").with(httpBasic("monitoring", "test-monitoring-password-1234")))
                .andExpect(status().isForbidden());
    }

    @Test
    void missingOrWeakPasswordsDoNotCreateAnAccessibleDefaultAccount() {
        var config = new MonitoringSecurityConfiguration();
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.security.core.userdetails.UsernameNotFoundException.class,
                () -> config.monitoringUsers("monitoring", "").loadUserByUsername("monitoring"));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> config.monitoringUsers("monitoring", "short"));
    }
}
