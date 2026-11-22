package com.benzaazaa.parcel.support.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.util.AntPathMatcher;

import java.util.List;

/**
 * Reads the bearer token from the {@code Authorization} header everywhere, and additionally from
 * the {@code access_token} query parameter on a short allow-list of GET paths (the SSE stream,
 * because the browser's EventSource cannot set headers). Tokens in URLs leak into logs, so the
 * allow-list is explicit and those endpoints only accept short-lived stream tokens.
 */
public class PathScopedBearerTokenResolver implements BearerTokenResolver {

    private final DefaultBearerTokenResolver headerOnly = new DefaultBearerTokenResolver();
    private final DefaultBearerTokenResolver headerOrQuery = new DefaultBearerTokenResolver();
    private final AntPathMatcher matcher = new AntPathMatcher();
    private final List<String> queryTokenPaths;

    public PathScopedBearerTokenResolver(List<String> queryTokenPaths) {
        this.queryTokenPaths = List.copyOf(queryTokenPaths);
        this.headerOrQuery.setAllowUriQueryParameter(true);
    }

    @Override
    public String resolve(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        boolean queryAllowed = queryTokenPaths.stream().anyMatch(pattern -> matcher.match(pattern, path));
        return (queryAllowed ? headerOrQuery : headerOnly).resolve(request);
    }
}
