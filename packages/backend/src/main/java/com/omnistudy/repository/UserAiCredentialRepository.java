package com.omnistudy.repository;

import com.omnistudy.model.entity.UserAiCredential;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserAiCredentialRepository extends JpaRepository<UserAiCredential, UUID> {
}

