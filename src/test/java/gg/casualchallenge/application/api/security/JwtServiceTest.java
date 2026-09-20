package gg.casualchallenge.application.api.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private static final String SECRET = "jwt-secret-key-for-tests-that-is-long-enough";

    private final JwtService jwtService = new JwtService(SECRET);

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

    @Test
    void testValidateToken() {
        assertTrue(jwtService.validateToken(jwtService.generateToken("discord-bot")));
        assertTrue(jwtService.validateToken(jwtService.generateToken("raoul", true)));
    }

    @Test
    void testValidateToken_withBrokenToken() {
        assertFalse(jwtService.validateToken(""));
        assertFalse(jwtService.validateToken("garbage"));
        assertFalse(jwtService.validateToken(new JwtService("another-secret-key-that-is-also-long-enough").generateToken("discord-bot")));
        assertFalse(jwtService.validateToken(expiredToken()));
    }

    private String expiredToken() {
        return Jwts.builder()
                .setSubject("discord-bot")
                .setExpiration(new Date(System.currentTimeMillis() - 1000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes()), SignatureAlgorithm.HS256)
                .compact();
    }
}
