package com.hyeja.engagementservice.global.config;

import com.hyeja.engagementservice.global.apiPayload.status.ErrorStatus;
import com.hyeja.engagementservice.global.exception.GeneralException;
import jakarta.servlet.http.*;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.*;
import org.springframework.web.servlet.config.annotation.*;

/** Services must only be reachable from the trusted gateway/internal network. */
@Configuration
public class GatewayIdentityConfig implements WebMvcConfigurer {
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                String id = request.getHeader("X-Member-Id");
                try {
                    if (id == null || Long.parseLong(id) <= 0) throw new NumberFormatException();
                } catch (NumberFormatException exception) {
                    throw new GeneralException(ErrorStatus.UNAUTHORIZED);
                }
                return true;
            }
        }).addPathPatterns("/api/favorite", "/api/favorite/**", "/api/notification", "/api/notification/**");
    }
}
