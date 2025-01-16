package gg.casualchallenge.application.api.security;

import gg.casualchallenge.application.common.Constants;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;

@Component
public class JwtService {

    private final byte[] secretKeyBytes;

    public JwtService(@Value("${casual-challenge.security.jwt.secret-key}") String secretKey) {
        this.secretKeyBytes = secretKey.getBytes();
    }

    public String generateToken(String username) {
        return Jwts.builder()
                .setSubject(username)
                .setIssuedAt(new Date())
                .setExpiration(Date.from(LocalDateTime.now().plusYears(5).toInstant(Constants.TIMEZONE)))
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

    public boolean validateToken(String token) {
        return extractClaims(token).getExpiration().after(new Date());
    }

    public static void main(String[] args) {
        SecretKey secretKey = Keys.secretKeyFor(SignatureAlgorithm.HS256);
        System.out.println(new String(secretKey.getEncoded(), StandardCharsets.UTF_8));
        System.out.println(Base64.getEncoder().encodeToString(secretKey.getEncoded()));

        JwtService jwtService = new JwtService("BOYbP5W6Ri2DKUp4aMCHshJecnrg3wF9ZlXyTNLQG10uSkAEdx");
        String token = jwtService.generateToken("discord-bot");
        System.out.println(token);
        System.out.println(jwtService.extractClaims(token));
        System.out.println(jwtService.validateToken(token));
    }
}
