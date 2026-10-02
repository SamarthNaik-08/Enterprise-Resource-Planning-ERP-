package com.erp.service;

import com.erp.entity.RefreshToken;
import com.erp.entity.User;
import com.erp.exception.BadRequestException;
import com.erp.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Issues, validates, rotates and revokes refresh tokens.
 *
 * Security properties:
 *  - raw token is 256 bits of SecureRandom entropy (opaque, not a JWT)
 *  - only the SHA-256 hash is stored; a leaked DB cannot yield valid tokens
 *  - rotation: every refresh invalidates the used token and issues a new one,
 *    so a stolen token stops working the moment the real user refreshes next
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Value("${app.jwt.refresh-expiration-ms}")
    private long refreshExpirationMs;

    /** Creates a new active refresh token for the user; returns the RAW token. */
    @Transactional
    public String issue(User user) {
        String raw = randomToken();
        refreshTokenRepository.save(RefreshToken.builder()
                .user(user)
                .tokenHash(sha256(raw))
                .expiresAt(LocalDateTime.now().plusNanos(refreshExpirationMs * 1_000_000))
                .build());
        return raw;
    }

    /**
     * Validates a raw refresh token and ROTATES it: the presented token is
     * revoked and a fresh one is issued. Returns the token's owner.
     *
     * noRollbackFor: the theft-detection path revokes ALL of the user's tokens
     * and THEN throws — Spring would otherwise roll back those revocations
     * together with the exception, silently re-arming the attacker's tokens.
     */
    @Transactional(noRollbackFor = BadRequestException.class)
    public User rotate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new BadRequestException("Refresh token is required");
        }
        RefreshToken stored = refreshTokenRepository.findByTokenHash(sha256(rawToken))
                .orElseThrow(() -> new BadRequestException("Invalid refresh token"));

        if (stored.isRevoked()) {
            // Reuse of an already-rotated token -> possible theft. Kill the family.
            revokeAllFor(stored.getUser());
            throw new BadRequestException("Refresh token already used — all sessions revoked");
        }
        if (!stored.isActive()) {
            throw new BadRequestException("Refresh token expired, please log in again");
        }

        stored.setRevoked(true); // single use
        return stored.getUser();
    }

    /** Real logout: the token can never be used again. */
    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(sha256(rawToken))
                .ifPresent(t -> t.setRevoked(true));
    }

    @Transactional
    public void revokeAllFor(User user) {
        refreshTokenRepository.findAllByUserIdAndRevokedFalse(user.getId())
                .forEach(t -> t.setRevoked(true));
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
