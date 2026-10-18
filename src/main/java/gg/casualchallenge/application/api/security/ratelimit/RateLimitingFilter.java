package gg.casualchallenge.application.api.security.ratelimit;

import gg.casualchallenge.application.api.security.JwtService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private final RateLimiterService rateLimiterService;
    private final JwtService jwtService;

    public RateLimitingFilter(RateLimiterService rateLimiterService, JwtService jwtService) {
        this.rateLimiterService = rateLimiterService;
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String authorizationHeader = request.getHeader("Authorization");

        if (hasBearerToken(authorizationHeader)) {
            String token = authorizationHeader.substring(7);

            try {
                Claims claims = jwtService.extractClaims(token);
                String username = claims.getSubject();
                if (!tryConsumeToken(claims, username)) {
                    response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                    response.getWriter().write("Rate limit exceeded for user: " + username);
                    return;
                }
            } catch (Exception e) {
                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                response.getWriter().write("Invalid or expired token");
                return;
            }
        }

        chain.doFilter(request, response);
    }

    private boolean hasBearerToken(String authorizationHeader) {
        if (authorizationHeader == null) return false;

        return authorizationHeader.startsWith("Bearer ");
    }

    private boolean tryConsumeToken(Claims claims, String username) {
        // Admins are not rate limited
        if (jwtService.isAdmin(claims)) return true;

        return rateLimiterService.consumeToken(username);
    }
}
