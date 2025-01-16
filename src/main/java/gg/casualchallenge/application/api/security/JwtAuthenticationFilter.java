package gg.casualchallenge.application.api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String authorizationHeader = request.getHeader("Authorization");

        String validToken = getValidToken(authorizationHeader);
        if (validToken != null) {
            SecurityContextHolder.getContext()
                    .setAuthentication(
                            new UsernamePasswordAuthenticationToken(
                                    jwtService.extractClaims(validToken).getSubject(), null, List.of()
                            )
                    );
        }

        chain.doFilter(request, response);
    }

    private String getValidToken(String authorizationHeader) {
        if (authorizationHeader == null) return null;
        if (!authorizationHeader.startsWith("Bearer ")) return null;
        String token = authorizationHeader.substring(7);
        if (jwtService.validateToken(token)) return token;

        return null;
    }
}
