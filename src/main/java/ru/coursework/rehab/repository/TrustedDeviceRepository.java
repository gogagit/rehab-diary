package ru.coursework.rehab.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import ru.coursework.rehab.domain.TrustedDevice;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TrustedDeviceRepository extends JpaRepository<TrustedDevice, Long> {
    List<TrustedDevice> findByAccountIdAndExpiresAtAfterOrderByIdDesc(Long accountId, Instant now);

    @Query("select d.account.username from TrustedDevice d where d.tokenHash = :hash and d.expiresAt > :now and d.account.deletedAt is null")
    Optional<String> findActiveLogin(String hash, Instant now);

    @Modifying
    @Query("delete from TrustedDevice d where d.id = :id and d.account.id = :accountId")
    int revoke(Long id, Long accountId);

    @Modifying
    @Query("delete from TrustedDevice d where d.tokenHash = :hash")
    void revokeByHash(String hash);
}
