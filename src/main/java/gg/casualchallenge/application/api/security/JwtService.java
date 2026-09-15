package gg.casualchallenge.application.api.security;

import gg.casualchallenge.application.common.Constants;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Date;

@Component
public class JwtService {

    private static final String CLAIM_ADMIN = "admin";

    private final byte[] secretKeyBytes;

    public JwtService(@Value("${casual-challenge.security.jwt.secret-key}") String secretKey) {
        this.secretKeyBytes = secretKey.getBytes();
    }

    public String generateToken(String username) {
        return generateToken(username, false);
    }

    public String generateToken(String username, boolean isAdmin) {
        LocalDateTime expiration = isAdmin
                ? LocalDateTime.now().plusYears(1) // an admin token can't be revoked, so don't let it live until 2031
                : LocalDateTime.now().plusYears(5);

        return Jwts.builder()
                .setSubject(username)
                .setIssuedAt(new Date())
                .setExpiration(Date.from(expiration.toInstant(Constants.TIMEZONE)))
                .claim(CLAIM_ADMIN, isAdmin)
                .signWith(Keys.hmacShaKeyFor(this.secretKeyBytes), SignatureAlgorithm.HS256)
                .compact();
    }

    public Claims extractClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(this.secretKeyBytes))
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public boolean isAdmin(Claims claims) {
        return Boolean.TRUE.equals(claims.get(CLAIM_ADMIN, Boolean.class)); // no claim --> not an admin, so old tokens keep working
    }

    public boolean validateToken(String token) {
        return extractClaims(token).getExpiration().after(new Date());
    }
}
