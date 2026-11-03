package com.hyeja.engagementservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.hyeja.engagementservice.client.policy.PolicySnapshotReader;
import com.hyeja.engagementservice.favorite.entity.Favorite;
import com.hyeja.engagementservice.favorite.repository.FavoriteRepository;
import com.hyeja.engagementservice.favorite.service.FavoriteService;
import com.hyeja.engagementservice.global.apiPayload.status.ErrorStatus;
import com.hyeja.engagementservice.global.exception.GeneralException;
import com.hyeja.engagementservice.notification.repository.NotificationRepository;
import com.hyeja.engagementservice.policy.entity.Policy;
import com.hyeja.engagementservice.policy.enums.PolicyApplyPeriod;
import com.hyeja.engagementservice.policy.enums.PolicyCategory;
import com.hyeja.engagementservice.policy.repository.PolicyRepository;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 실제 Feign GET/JSON과 H2 저장을 연결합니다. 실제 Policy/Eureka는 별도 검증 대상입니다. */
@SpringBootTest
@ActiveProfiles("test")
class PolicyFavoriteIntegrationTest {
    private static final String SUCCESS = """
            {"isSuccess":true,"code":"SUCCESS_001","message":"성공","result":{
              "policyId":"F5-P1","policyName":"최신 정책","categories":["MONTHLY_RENT","JEONSE"],
              "supportContent":null,"applyEndDate":"2026-01-01","applyPeriodCode":"CLOSED",
              "applyUrl":null,"activeYn":true,"deleted":false}}
            """;
    private static final CopyOnWriteArrayList<String> requests = new CopyOnWriteArrayList<>();
    private static final ExecutorService executor = Executors.newCachedThreadPool();
    private static final HttpServer server = startServer();
    private static volatile int status = 200;
    private static volatile String body = SUCCESS;
    private static volatile long delayMillis;
    private static volatile boolean receivedAuthentication;

    @Autowired FavoriteService service;
    @Autowired PolicyRepository policies;
    @Autowired NotificationRepository notifications;
    @MockitoSpyBean FavoriteRepository favorites;
    @MockitoSpyBean PolicySnapshotReader snapshotReader;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("clients.policy.url", () -> "http://127.0.0.1:" + server.getAddress().getPort());
        registry.add("spring.cloud.openfeign.client.config.policy-service.read-timeout", () -> 200);
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:f5-favorite;MODE=MariaDB;DB_CLOSE_DELAY=-1");
    }

    @BeforeEach
    void setUp() {
        notifications.deleteAll();
        favorites.deleteAll();
        policies.deleteAll();
        requests.clear();
        status = 200;
        body = SUCCESS;
        delayMillis = 0;
        receivedAuthentication = false;
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
        executor.shutdownNow();
    }

    @Test
    void missingSnapshotIsCreatedFromF5WithoutFilteringExpiredOrClosedPolicies() {
        // 외부 호출 시 트랜잭션이 없고, 응답 후 로컬 저장 트랜잭션이 시작되는지 확인합니다.
        org.mockito.Mockito.doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return invocation.callRealMethod();
        }).when(snapshotReader).getPolicy("F5-P1");

        var result = service.createFavorite(1L, "F5-P1");

        assertThat(requests).containsExactly("GET /internal/policies/F5-P1");
        assertThat(receivedAuthentication).isFalse();
        assertThat(result.getPolicyId()).isEqualTo("F5-P1");
        assertThat(result.getPolicyName()).isEqualTo("최신 정책");
        assertThat(result.getCategoryCodes()).containsExactlyInAnyOrder(PolicyCategory.MONTHLY_RENT, PolicyCategory.JEONSE);
        assertThat(result.getSupportContent()).isNull();
        assertThat(result.getApplyUrl()).isNull();
        assertThat(result.getApplyEndDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(result.getApplyPeriodCode()).isEqualTo(PolicyApplyPeriod.CLOSED);
        assertThat(result.getCreatedAt()).isNotNull();
        assertThat(policies.count()).isEqualTo(1);
        assertThat(favorites.count()).isEqualTo(1);
    }

    @Test
    void existingSnapshotIsRefreshedAndEachRegistrationCallsF5() {
        saveStaleSnapshot();
        service.createFavorite(1L, "F5-P1");
        body = SUCCESS.replace("최신 정책", "더 최신 정책");
        service.createFavorite(2L, "F5-P1");

        assertThat(requests).hasSize(2);
        assertThat(policies.findById("F5-P1").orElseThrow().getPolicyName()).isEqualTo("더 최신 정책");
        assertThat(policies.findById("F5-P1").orElseThrow().getActiveYn()).isTrue();
        assertThat(policies.count()).isEqualTo(1);
        assertThat(favorites.count()).isEqualTo(2);
    }

    @Test
    void currentF5ResponseClearsStaleLocalDeletionFlag() {
        saveStaleSnapshot();
        Policy policy = policies.findById("F5-P1").orElseThrow();
        policy.softDelete();
        policies.saveAndFlush(policy);

        service.createFavorite(1L, "F5-P1");

        assertThat(policies.findById("F5-P1").orElseThrow().isDeleted()).isFalse();
        assertThat(favorites.count()).isEqualTo(1);
    }

    @Test
    void duplicateRegistrationKeepsConflictAndRollsBackSnapshotUpdate() {
        service.createFavorite(1L, "F5-P1");
        body = SUCCESS.replace("최신 정책", "중복 요청에서 받은 이름");

        assertFailure(ErrorStatus.FAVORITE_ALREADY_EXISTS);

        assertThat(requests).hasSize(2);
        assertThat(policies.findById("F5-P1").orElseThrow().getPolicyName()).isEqualTo("최신 정책");
        assertThat(favorites.count()).isEqualTo(1);
    }

    @Test
    void favoriteInsertFailureRollsBackNewSnapshot() {
        failFavoriteInsert();

        assertFailure(ErrorStatus.FAVORITE_ALREADY_EXISTS);

        assertThat(policies.count()).isZero();
        assertThat(favorites.count()).isZero();
    }

    @Test
    void favoriteInsertFailureRollsBackExistingSnapshotChanges() {
        saveStaleSnapshot();
        failFavoriteInsert();

        assertFailure(ErrorStatus.FAVORITE_ALREADY_EXISTS);

        assertStaleSnapshotUnchanged();
        assertThat(favorites.count()).isZero();
    }

    @Test
    void policyNotFoundIsReturnedEvenWhenSnapshotExists() {
        saveStaleSnapshot();
        status = 404;
        body = "{\"isSuccess\":false,\"code\":\"POLICY_001\",\"message\":\"없음\",\"result\":null}";

        assertFailure(ErrorStatus.POLICY_NOT_FOUND);

        assertStaleSnapshotUnchanged();
        assertThat(favorites.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 404, 500, 503})
    void otherHttpErrorsDoNotBecomePolicyNotFoundOrUseStaleSnapshot(int httpStatus) {
        saveStaleSnapshot();
        status = httpStatus;
        body = "{\"isSuccess\":false,\"code\":\"SERVER_001\",\"result\":null}";

        assertFailure(ErrorStatus.POLICY_SERVICE_UNAVAILABLE);

        assertStaleSnapshotUnchanged();
        assertThat(favorites.count()).isZero();
    }

    @Test
    void readTimeoutDoesNotSaveFavoriteOrChangeSnapshot() {
        saveStaleSnapshot();
        delayMillis = 1000;

        assertFailure(ErrorStatus.POLICY_SERVICE_UNAVAILABLE);

        assertStaleSnapshotUnchanged();
        assertThat(favorites.count()).isZero();
        assertThat(requests).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "{}", "not json",
            "{\"isSuccess\":true,\"code\":\"SUCCESS_001\",\"result\":null}",
            "{\"isSuccess\":false,\"code\":\"POLICY_001\",\"result\":null}"})
    void invalidEnvelopeDoesNotSave(String invalidBody) {
        body = invalidBody;

        assertFailure(ErrorStatus.POLICY_SERVICE_UNAVAILABLE);

        assertThat(policies.count()).isZero();
        assertThat(favorites.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"wrong-id", "inactive", "deleted", "missing-deleted", "missing-categories", "unknown-enum", "bad-date"})
    void invalidSnapshotDoesNotSave(String invalidField) {
        body = switch (invalidField) {
            case "wrong-id" -> SUCCESS.replace("F5-P1", "OTHER");
            case "inactive" -> SUCCESS.replace("\"activeYn\":true", "\"activeYn\":false");
            case "deleted" -> SUCCESS.replace("\"deleted\":false", "\"deleted\":true");
            case "missing-deleted" -> SUCCESS.replace(",\"deleted\":false", "");
            case "missing-categories" -> SUCCESS.replace("[\"MONTHLY_RENT\",\"JEONSE\"]", "null");
            case "unknown-enum" -> SUCCESS.replace("CLOSED", "INVALID");
            default -> SUCCESS.replace("2026-01-01", "not-a-date");
        };

        assertFailure(ErrorStatus.POLICY_SERVICE_UNAVAILABLE);

        assertThat(policies.count()).isZero();
        assertThat(favorites.count()).isZero();
    }

    @Test
    void deleteKeepsExistingBehaviorWithoutCallingF5() {
        service.createFavorite(1L, "F5-P1");
        status = 500;

        service.deleteFavorite(1L, "F5-P1");

        assertThat(favorites.count()).isZero();
        assertThat(requests).hasSize(1);
    }

    private void assertFailure(ErrorStatus error) {
        assertThatThrownBy(() -> service.createFavorite(1L, "F5-P1"))
                .isInstanceOfSatisfying(GeneralException.class, ex -> assertThat(ex.getCode()).isEqualTo(error));
    }

    private void failFavoriteInsert() {
        doThrow(new DataIntegrityViolationException("simulated favorite insert failure"))
                .when(favorites).saveAndFlush(any(Favorite.class));
    }

    private void saveStaleSnapshot() {
        Policy policy = new Policy();
        policy.setPolicyId("F5-P1");
        policy.setPolicyName("이전 정책");
        policy.setCategories(Set.of(PolicyCategory.OTHER));
        policy.setActiveYn(false);
        policies.saveAndFlush(policy);
    }

    private void assertStaleSnapshotUnchanged() {
        Policy policy = policies.findById("F5-P1").orElseThrow();
        assertThat(policy.getPolicyName()).isEqualTo("이전 정책");
        assertThat(policy.getActiveYn()).isFalse();
        assertThat(policy.getCategories()).containsExactly(PolicyCategory.OTHER);
    }

    private static HttpServer startServer() {
        try {
            HttpServer http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            http.setExecutor(executor);
            http.createContext("/internal/policies/", exchange -> {
                requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
                receivedAuthentication = exchange.getRequestHeaders().containsKey("Authorization")
                        || exchange.getRequestHeaders().containsKey("X-Internal-Token")
                        || exchange.getRequestHeaders().containsKey("X-Member-Id");
                int responseStatus = status;
                byte[] responseBody = body.getBytes(StandardCharsets.UTF_8);
                long delay = delayMillis;
                try (exchange) {
                    if (delay > 0) Thread.sleep(delay);
                    exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                    exchange.sendResponseHeaders(responseStatus, responseBody.length);
                    exchange.getResponseBody().write(responseBody);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                } catch (IOException clientDisconnected) {
                    // 타임아웃 뒤 클라이언트가 연결을 닫는 것은 예상한 동작입니다.
                }
            });
            http.start();
            return http;
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}
