package gg.casualchallenge.application.api.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private final JwtService jwtService = new JwtService("jwt-secret-key-for-tests-that-is-long-enough");

    @Test
    void testGenerateToken() {
        Claims claims = jwtService.extractClaims(jwtService.generateToken("discord-bot"));

        assertEquals("discord-bot", claims.getSubject());
        assertFalse(jwtService.isAdmin(claims));
    }

    @Test
    void testGenerateToken_withAdmin() {
        Claims claims = jwtService.extractClaims(jwtService.generateToken("raoul", true));

        assertEquals("raoul", claims.getSubject());
        assertTrue(jwtService.isAdmin(claims));
    }
}
