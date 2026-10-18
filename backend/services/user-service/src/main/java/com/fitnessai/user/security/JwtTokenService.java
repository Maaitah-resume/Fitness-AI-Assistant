package com.fitnessai.user.security;

import com.fitnessai.user.model.User;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class JwtTokenService {

    private final JwtEncoder encoder;
    private final String keyId;
    private final String issuer;
    private final long expiryMinutes;

    public JwtTokenService(RsaKeyProvider keys,
                           @Value("${jwt.issuer}") String issuer,
                           @Value("${jwt.expiry-minutes}") long expiryMinutes) {
        this.encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(keys.rsaKey())));
        this.keyId = keys.rsaKey().getKeyID();
        this.issuer = issuer;
        this.expiryMinutes = expiryMinutes;
    }

    public String issue(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(String.valueOf(user.getId()))
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiresAt(now.plus(expiryMinutes, ChronoUnit.MINUTES))
                .claim("username", user.getUsername())
                .claim("role", user.getRole().name())
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(keyId).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public long expirySeconds() {
        return expiryMinutes * 60;
    }
}
