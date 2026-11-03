package com.hyeja.config.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = ConfigSecurityConfigurationTest.TestConfiguration.class)
@TestPropertySource(properties = {
        "config-security.admin-username=config-admin",
        "config-security.admin-password=test-bus-admin-password"
})
class ConfigSecurityConfigurationTest {
    @Configuration(proxyBeanMethods = false)
    @EnableWebSecurity
    @EnableWebMvc
    @Import({ConfigSecurityConfiguration.class, Routes.class})
    static class TestConfiguration {}

    // Stub handlers isolate authorization from Kafka and the external Config Git repository.
    @RestController
    static class Routes {
        @PostMapping({"/actuator/busrefresh", "/actuator/busrefresh/{destination}"})
        void refresh() {}

        @GetMapping({"/policy-service/dev", "/actuator/health", "/actuator/env"})
        String read() { return "ok"; }
    }

    @Autowired WebApplicationContext context;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void refreshRequiresAdministratorAndAcceptsBasicAuthWithoutCsrfToken() throws Exception {
        mvc.perform(post("/actuator/busrefresh")).andExpect(status().isUnauthorized());
        mvc.perform(post("/actuator/busrefresh").with(httpBasic("config-admin", "wrong")))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/actuator/busrefresh").with(user("reader").roles("CONFIG_READER")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/actuator/busrefresh").with(httpBasic("config-admin", "test-bus-admin-password")))
                .andExpect(status().isOk());
        mvc.perform(post("/actuator/busrefresh/policy-service:**")
                .with(httpBasic("config-admin", "test-bus-admin-password")))
                .andExpect(status().isOk());
    }

    @Test
    void configReadsAndHealthStayPublicButOtherActuatorEndpointsAreDenied() throws Exception {
        mvc.perform(get("/policy-service/dev")).andExpect(status().isOk());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/actuator/env").with(httpBasic("config-admin", "test-bus-admin-password")))
                .andExpect(status().isForbidden());
    }
}
