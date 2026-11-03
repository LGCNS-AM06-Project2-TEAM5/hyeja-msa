package com.hyeja.policyservice.client.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hyeja.policyservice.client.user.dto.MemberProfileDTO;
import com.hyeja.policyservice.client.user.enums.EmploymentStatus;
import com.hyeja.policyservice.client.user.enums.IncomeRange;
import com.hyeja.policyservice.global.apiPayload.status.ErrorStatus;
import com.hyeja.policyservice.global.exception.GeneralException;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

// 실제 Feign이 user-service 응답 JSON(docs/api F1·F6 예시)을 받아 옮겨 담는지, 오류 코드를 바꾸는지 확인합니다.
// user-service 대신 JDK HttpServer를 띄우고 Feign 주소만 그쪽으로 돌립니다.
@SpringBootTest
@ActiveProfiles("test")
class UserClientIntegrationTest {

    private static final HttpServer USER_SERVICE = startUserService();

    @Autowired
    private MemberProfileReader reader;

    @DynamicPropertySource
    static void userServiceUrl(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.openfeign.client.config.userClient.url",
                () -> "http://localhost:" + USER_SERVICE.getAddress().getPort());
    }

    @AfterAll
    static void stop() {
        USER_SERVICE.stop(0);
    }

    @Test
    void 내부_토큰을_붙여_보내고_회원_조건_응답을_옮겨_담는다() {
        MemberProfileDTO profile = reader.getActiveProfile(1L);

        assertThat(profile.memberId()).isEqualTo(1L);
        assertThat(profile.birth()).isEqualTo(LocalDate.of(2000, 3, 15));
        assertThat(profile.region().regionCode()).isEqualTo("11440");
        assertThat(profile.region().sigunguName()).isEqualTo("서울특별시 마포구");
        assertThat(profile.employmentCode()).isEqualTo(EmploymentStatus.EMPLOYED);
        assertThat(profile.houselessYn()).isTrue();
        assertThat(profile.incomeRangeCode()).isEqualTo(IncomeRange.R2000_3000);
    }

    @Test
    void 조건이_없다는_404는_PROFILE_001로_바꾼다() {
        assertThatThrownBy(() -> reader.getActiveProfile(2L))
                .isInstanceOf(GeneralException.class)
                .extracting(e -> ((GeneralException) e).getCode())
                .isEqualTo(ErrorStatus.PROFILE_NOT_FOUND);
    }

    @Test
    void 서버_오류는_응답_불가로_바꾼다() {
        assertThatThrownBy(() -> reader.getActiveProfile(3L))
                .isInstanceOf(GeneralException.class)
                .extracting(e -> ((GeneralException) e).getCode())
                .isEqualTo(ErrorStatus.USER_SERVICE_UNAVAILABLE);
    }

    private static HttpServer startUserService() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            // 1번은 실제 user처럼 X-Internal-Token이 맞아야 응답합니다. 헤더가 빠지면 401이라 첫 테스트가 깨집니다.
            server.createContext("/internal/members/1/profile", exchange -> respond(exchange,
                    "test-internal-token".equals(exchange.getRequestHeaders().getFirst("X-Internal-Token")) ? 200 : 401,
                    """
                    {"isSuccess":true,"code":"SUCCESS_001","message":"성공입니다.","result":{
                      "memberId":1,"birth":"2000-03-15",
                      "region":{"regionCode":"11440","sigunguName":"서울특별시 마포구"},
                      "employmentCode":"EMPLOYED","houselessYn":true,"marriageCode":"SINGLE",
                      "incomeRangeCode":"R2000_3000","educationCode":null,"housingType":"MONTHLY_RENT"}}
                    """));
            server.createContext("/internal/members/2/profile", exchange -> respond(exchange, 404, """
                    {"isSuccess":false,"code":"PROFILE_001","message":"등록된 조건이 없습니다.","result":null}
                    """));
            server.createContext("/internal/members/3/profile", exchange -> respond(exchange, 500, """
                    {"isSuccess":false,"code":"SERVER_001","message":"서버 오류가 발생했습니다.","result":null}
                    """));
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
