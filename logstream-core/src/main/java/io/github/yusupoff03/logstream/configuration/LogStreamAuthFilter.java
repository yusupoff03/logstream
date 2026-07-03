package io.github.yusupoff03.logstream.configuration;


import io.github.yusupoff03.logstream.model.LogStreamSession;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class LogStreamAuthFilter extends OncePerRequestFilter {

    private final SessionManager sessionManager;
    private final LogStreamProperties properties;

    public LogStreamAuthFilter(SessionManager sessionManager, LogStreamProperties properties) {
        this.sessionManager = sessionManager;
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        if (!properties.getAuth().isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        String uri = request.getRequestURI();

        if (isStaticAsset(uri)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (uri.endsWith("/login") || uri.endsWith("/logout")) {
            filterChain.doFilter(request, response);
            return;
        }

        Cookie[] cookies = request.getCookies();

        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("LOGSTREAM_SESSION".equals(cookie.getName())) {
                    LogStreamSession session = sessionManager.find(cookie.getValue());
                    if (session != null) {
                        filterChain.doFilter(request, response);
                        return;
                    }
                    break;
                }
            }
        }

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"message\":\"Unauthorized\"}");
    }

    private boolean isStaticAsset(String uri) {
        return uri.endsWith(".html")
                || uri.endsWith(".js")
                || uri.endsWith(".css")
                || uri.endsWith(".ico")
                || uri.endsWith(".png")
                || uri.endsWith(".svg");
    }

}
