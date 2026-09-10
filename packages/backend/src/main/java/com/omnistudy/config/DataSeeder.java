package com.omnistudy.config;

import com.omnistudy.model.entity.User;
import com.omnistudy.model.entity.UserQuota;
import com.omnistudy.repository.UserRepository;
import com.omnistudy.repository.UserQuotaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;

@Slf4j
@Component
@Order(10)
@RequiredArgsConstructor
public class DataSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserQuotaRepository quotaRepository;

    @Value("${app.default-user.enabled:true}")
    private boolean enabled;

    @Value("${app.default-user.username:test}")
    private String defaultUsername;

    @Value("${app.default-user.password:test123}")
    private String defaultPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.info("Default development user seed is disabled.");
            return;
        }
        if (userRepository.findByUsername(defaultUsername).isPresent()) {
            log.info("Default user '{}' already exists, skipping seed.", defaultUsername);
            return;
        }
        User user = userRepository.save(User.builder()
                .username(defaultUsername)
                .passwordHash(passwordEncoder.encode(defaultPassword))
                .createdAt(OffsetDateTime.now())
                .settings(Map.of())
                .build());
        quotaRepository.save(UserQuota.builder().userId(user.getId()).build());
        log.info("Seeded default development user: username={}", defaultUsername);
    }
}
