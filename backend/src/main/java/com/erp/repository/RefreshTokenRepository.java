package com.erp.repository;

import com.erp.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findAllByUserIdAndRevokedFalse(Long userId);

    @Modifying
    void deleteByUserId(Long userId);

    /** Housekeeping: purge expired rows (call from a scheduled job if desired). */
    @Modifying
    void deleteByExpiresAtBefore(LocalDateTime cutoff);
}
