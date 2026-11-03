package verification.f2;

import com.hyeja.policyservice.PolicyServiceApplication;
import com.hyeja.recommendationservice.RecommendationServiceApplication;
import com.hyeja.recommendationservice.policy.enums.PolicyCategory;
import com.hyeja.recommendationservice.policy.service.PolicySearchAiAnalyzer;
import jakarta.servlet.Filter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.SpringApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.util.ContentCachingRequestWrapper;

/**
 * #77 검증 전용 실행 진입점. 운영 JAR은 수정하지 않습니다.
 * Recommendation의 AI만 대체하고 F1·F2·F3 Feign·판정·분류는 실제 구현을 사용합니다.
 * Policy에서는 합성 데이터의 F2 요청만 기록해 카테고리·지역·한국 날짜 전달을 확인합니다.
 * 운영 환경에서 사용하지 않으며, 기록 파일에 실제 회원 정보를 넣지 않습니다.
 */
public final class F2LiveFixture {
    private F2LiveFixture() {}

    public static final class Recommendation {
        public static void main(String[] args) {
            new SpringApplication(RecommendationServiceApplication.class, AiConfig.class).run(args);
        }
    }

    public static final class Policy {
        public static void main(String[] args) {
            new SpringApplication(PolicyServiceApplication.class, WireConfig.class).run(args);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class AiConfig {
        @Bean
        @Primary
        PolicySearchAiAnalyzer verificationAiAnalyzer() {
            return new PolicySearchAiAnalyzer() {
                @Override
                public SearchIntent analyzeIntent(String query) {
                    // 자연어 품질 검증이 아니라 F2 조건 전달을 재현하기 위한 고정 의도입니다.
                    if (query.equals("전체 주거 정책")) return new SearchIntent(true, Set.of());
                    return new SearchIntent(true, Set.of(query.contains("전세")
                            ? PolicyCategory.JEONSE : PolicyCategory.MONTHLY_RENT));
                }

                @Override
                public Map<String, AiAssessment> assess(List<ReasonRequest> requests) {
                    // 원래 검색 서비스의 판정 결과와 기본 이유 생성 경로를 검증합니다.
                    throw new AnalysisException(FailureType.UNAVAILABLE, "F2 검증에서는 외부 AI를 호출하지 않습니다.");
                }
            };
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class WireConfig {
        @Bean
        Filter verificationF2RequestRecorder() {
            return (request, response, chain) -> {
                var http = (jakarta.servlet.http.HttpServletRequest) request;
                if (!"/internal/policies/candidates".equals(http.getRequestURI())) {
                    chain.doFilter(request, response);
                    return;
                }
                var cached = new ContentCachingRequestWrapper(http, 8192);
                chain.doFilter(cached, response);
                Files.writeString(Path.of(System.getProperty("f2.wire.file")),
                        new String(cached.getContentAsByteArray(), StandardCharsets.UTF_8) + "\n",
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            };
        }
    }
}
