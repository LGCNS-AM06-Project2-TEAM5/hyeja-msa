package com.hyeja.config.security;

import java.util.Arrays;
import java.util.function.Supplier;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

/** Readers may only use the JSON Environment endpoint for their exact application. */
public final class ApplicationConfigAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {
    private final ConfigAccessProperties properties;
    public ApplicationConfigAuthorizationManager(ConfigAccessProperties properties) { this.properties = properties; }

    @Override
    public AuthorizationResult authorize(Supplier<? extends Authentication> authentication, RequestAuthorizationContext context) {
        Authentication user = authentication.get();
        if (user == null || !user.isAuthenticated()) return new AuthorizationDecision(false);
        if (user.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_CONFIG_ADMIN"))) {
            return new AuthorizationDecision(true);
        }
        String path = context.getRequest().getRequestURI().substring(context.getRequest().getContextPath().length());
        if (path.contains("%") || path.contains(";") || path.contains("\\")) return new AuthorizationDecision(false);
        String[] parts = path.split("/", -1);
        if (parts.length != 3 && parts.length != 4) return new AuthorizationDecision(false);
        boolean ownsApplication = user.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("CONFIG_APP:" + parts[1]));
        boolean profilesAllowed = Arrays.stream(parts[2].split(",", -1))
                .allMatch(properties.allowedProfiles()::contains);
        boolean labelAllowed = parts.length == 3 || properties.allowedLabels().contains(parts[3]);
        return new AuthorizationDecision(ownsApplication && profilesAllowed && labelAllowed);
    }
}
