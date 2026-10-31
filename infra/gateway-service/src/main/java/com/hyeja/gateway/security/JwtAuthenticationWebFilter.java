package com.hyeja.gateway.security;

import io.jsonwebtoken.JwtException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.cors.reactive.CorsUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

// Security 체인 안에만 등록하여 WebFilter 이중 등록을 방지합니다.
final class JwtAuthenticationWebFilter implements WebFilter {
    private final JwtVerifier verifier;
    private final TokenBlacklist blacklist;

    JwtAuthenticationWebFilter(JwtVerifier verifier, TokenBlacklist blacklist) {
        this.verifier = verifier;
        this.blacklist = blacklist;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (CorsUtils.isPreFlightRequest(exchange.getRequest())) {
            return chain.filter(exchange);
        }
        // 외부에서 주입한 사용자 헤더는 신뢰하지 않습니다. 원래 JWT는 그대로 전달합니다.
        var clean = exchange.mutate().request(request -> request.headers(headers -> {
            headers.remove("X-Member-Id");
            headers.remove("X-Member-Role");
        })).build();
        var headers = clean.getRequest().getHeaders().get(HttpHeaders.AUTHORIZATION);
        if (headers == null) {
            return chain.filter(clean);
        }
        if (headers.size() != 1 || !headers.get(0).startsWith("Bearer ")) {
            return SecurityError.unauthorized(clean);
        }
        String token = headers.get(0).substring(7);
        JwtVerifier.Identity identity;
        try {
            identity = verifier.verify(token);
        } catch (JwtException | IllegalArgumentException e) {
            return SecurityError.unauthorized(clean);
        }
        // Redis 장애는 미차단으로 취급하지 않습니다. downstream 오류까지 잡지 않도록 조회만 변환합니다.
        return blacklist.contains(token).map(blocked -> blocked ? "blocked" : "allowed")
                .onErrorReturn("unavailable")
                .flatMap(state -> {
                    if (state.equals("blocked")) {
                        return SecurityError.unauthorized(clean);
                    }
                    if (state.equals("unavailable")) {
                        return SecurityError.write(clean, HttpStatus.SERVICE_UNAVAILABLE,
                                "GATEWAY_001", "인증 서비스를 일시적으로 사용할 수 없습니다.");
                    }
                    var authentication = new UsernamePasswordAuthenticationToken(identity.memberId(), token,
                            List.of(new SimpleGrantedAuthority("ROLE_" + identity.role())));
                    var forwarded = clean.mutate().request(request -> request.headers(h -> {
                        h.set("X-Member-Id", Long.toString(identity.memberId()));
                        h.set("X-Member-Role", identity.role());
                    })).build();
                    return chain.filter(forwarded)
                            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));
                });
    }
}
