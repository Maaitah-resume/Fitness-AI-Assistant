package com.fitnessai.user.security;

import com.nimbusds.jose.jwk.JWKSet;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Public key set every other service points its resource-server config at. */
@RestController
public class JwksController {

    private final Map<String, Object> jwks;

    public JwksController(RsaKeyProvider keys) {
        this.jwks = new JWKSet(keys.rsaKey().toPublicJWK()).toJSONObject();
    }

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        return jwks;
    }
}
