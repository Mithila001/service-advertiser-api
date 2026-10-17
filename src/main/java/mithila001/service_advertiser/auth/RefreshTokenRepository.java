package mithila001.service_advertiser.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    // Read only the owner before locking, so token state is loaded after the user lock.
    @Query("select t.userId from RefreshToken t where t.tokenHash = :tokenHash")
    Optional<Long> findUserIdByTokenHash(@Param("tokenHash") String tokenHash);

    @Modifying
    @Query("delete from RefreshToken t where t.expiresAt <= :now")
    int deleteExpired(@Param("now") Instant now);

    @Modifying
    @Query("update RefreshToken t set t.revokedAt = :now where t.familyId = :familyId and t.revokedAt is null")
    int revokeFamily(@Param("familyId") UUID familyId, @Param("now") Instant now);
}
