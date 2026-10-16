package com.example.recruitment.security;

import com.example.recruitment.entity.AppUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/** Creates and reads the login token. */
@Service
public class JwtService {

    private final SecretKey key;
    private final long expiryMillis;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expiry-hours:12}") long expiryHours) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiryMillis = expiryHours * 60L * 60L * 1000L;
    }

   
public static final String PORTAL_ADMIN = "ADMIN";
public static final String PORTAL_CLIENT = "CLIENT";

/** portalType = PORTAL_ADMIN (Admin Portal login) or PORTAL_CLIENT (client website login). */
public String generate(AppUser user, Long selectedClientId, String portalType) {
    Date now = new Date();
    return Jwts.builder()
            .subject(user.getEmail())
            .claim("clientId", selectedClientId)
            .claim("portal", portalType)
            .issuedAt(now)
            .expiration(new Date(now.getTime() + expiryMillis))
            .signWith(key)
            .compact();
}


    /** Throws a JwtException if the token is invalid or expired. */
    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
