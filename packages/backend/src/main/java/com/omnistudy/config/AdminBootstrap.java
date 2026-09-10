package com.omnistudy.config;

import com.omnistudy.model.entity.*;
import com.omnistudy.repository.UserQuotaRepository;
import com.omnistudy.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Slf4j
@Component
@Order(20)
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {
    private final UserRepository userRepository;
    private final UserQuotaRepository quotaRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.bootstrap-admin.username:}") private String username;
    @Value("${app.bootstrap-admin.password:}") private String password;
    @Value("${app.bootstrap-admin.email:}") private String email;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (username == null || username.isBlank()) return;
        String normalized = username.trim().toLowerCase(Locale.ROOT);
        User user = userRepository.findByUsername(normalized).orElse(null);
        if (user == null) {
            if (password == null || password.length() < 8) {
                log.warn("Bootstrap admin '{}' was not created: BOOTSTRAP_ADMIN_PASSWORD must contain at least 8 characters", normalized);
                return;
            }
            user = userRepository.save(User.builder()
                    .username(normalized)
                    .email(email == null || email.isBlank() ? null : email.trim().toLowerCase(Locale.ROOT))
                    .passwordHash(passwordEncoder.encode(password))
                    .role(UserRole.ADMIN).status(UserStatus.ACTIVE).build());
            log.info("Created bootstrap admin '{}'.", normalized);
        } else {
            user.setRole(UserRole.ADMIN);
            user.setStatus(UserStatus.ACTIVE);
            log.info("Ensured bootstrap user '{}' has ADMIN access.", normalized);
        }
        var adminId = user.getId();
        UserQuota quota = quotaRepository.findById(adminId)
                .orElseGet(() -> UserQuota.builder().userId(adminId).build());
        quota.setUnlimited(true);
        quotaRepository.save(quota);
    }
}
