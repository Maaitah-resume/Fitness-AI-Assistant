package com.fitnessai.user.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.RSAKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Owns the RS256 signing key. Loaded from {@code jwt.key-dir} if present,
 * otherwise generated and written there, so tokens (and every downstream
 * service's cached JWKS) stay valid across restarts. Mount that directory as
 * a volume in docker-compose; without one, a container rebuild rotates the key.
 */
@Component
public class RsaKeyProvider {

    private static final Logger log = LoggerFactory.getLogger(RsaKeyProvider.class);
    private static final String PRIVATE_FILE = "jwt-private.key";
    private static final String PUBLIC_FILE = "jwt-public.key";

    private final RSAKey rsaKey;

    public RsaKeyProvider(@Value("${jwt.key-dir}") String keyDir) {
        try {
            KeyPair pair = loadOrCreate(Path.of(keyDir));
            this.rsaKey = new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                    .privateKey((RSAPrivateKey) pair.getPrivate())
                    .keyIDFromThumbprint()
                    .build();
        } catch (IOException | GeneralSecurityException | JOSEException e) {
            throw new IllegalStateException("Could not initialise JWT signing key", e);
        }
    }

    public RSAKey rsaKey() {
        return rsaKey;
    }

    public RSAPublicKey publicKey() {
        try {
            return rsaKey.toRSAPublicKey();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    private static KeyPair loadOrCreate(Path dir) throws IOException, GeneralSecurityException {
        Path priv = dir.resolve(PRIVATE_FILE);
        Path pub = dir.resolve(PUBLIC_FILE);
        KeyFactory kf = KeyFactory.getInstance("RSA");

        if (Files.exists(priv) && Files.exists(pub)) {
            byte[] privBytes = Base64.getDecoder().decode(Files.readString(priv).trim());
            byte[] pubBytes = Base64.getDecoder().decode(Files.readString(pub).trim());
            log.info("Loaded JWT signing key from {}", dir.toAbsolutePath());
            return new KeyPair(
                    kf.generatePublic(new X509EncodedKeySpec(pubBytes)),
                    kf.generatePrivate(new PKCS8EncodedKeySpec(privBytes)));
        }

        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair pair = gen.generateKeyPair();
        Files.createDirectories(dir);
        Files.writeString(priv, Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()));
        Files.writeString(pub, Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()));
        log.warn("Generated a new JWT signing key in {} — previously issued tokens are now invalid.",
                dir.toAbsolutePath());
        return pair;
    }
}
