package com.omnistudy.repository;

import com.omnistudy.model.entity.UserQuota;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface UserQuotaRepository extends JpaRepository<UserQuota, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from UserQuota q where q.userId = :userId")
    Optional<UserQuota> findByUserIdForUpdate(UUID userId);
}
