package gg.casualchallenge.application.api.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
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
            Claims claims = jwtService.extractClaims(validToken);
            List<GrantedAuthority> authorities = jwtService.isAdmin(claims)
                    ? List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
                    : List.of();

            SecurityContextHolder.getContext()
                    .setAuthentication(
                            new UsernamePasswordAuthenticationToken(
                                    claims.getSubject(), null, authorities
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
