package com.omnistudy.service;

import com.omnistudy.model.dto.AdminOverviewResponse;
import com.omnistudy.model.dto.AdminUpdateUserRequest;
import com.omnistudy.model.dto.AdminUserResponse;
import com.omnistudy.model.entity.*;
import com.omnistudy.repository.UsageRecordRepository;
import com.omnistudy.repository.UserQuotaRepository;
import com.omnistudy.repository.UserRepository;
import com.omnistudy.repository.security.CachedUserAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminService {
    private final UserRepository userRepository;
    private final UserQuotaRepository quotaRepository;
    private final UsageRecordRepository usageRepository;
    private final CachedUserAccessService cachedUserAccessService;

    @Transactional(readOnly = true)
    public List<AdminUserResponse> users() {
        OffsetDateTime today = today();
        return userRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .limit(200).map(user -> response(user, quotaRepository.findById(user.getId())
                        .orElseGet(() -> UserQuota.builder().userId(user.getId()).build()), today)).toList();
    }

    @Transactional
    public AdminUserResponse update(UUID actorId, UUID userId, AdminUpdateUserRequest request) {
        User user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        if (actorId.equals(userId) && (request.role() == UserRole.USER || request.status() == UserStatus.DISABLED)) {
            throw new IllegalArgumentException("不能取消自己的管理员权限或停用自己的账号");
        }
        if (request.role() != null) user.setRole(request.role());
        if (request.status() != null) user.setStatus(request.status());
        user.setUpdatedAt(OffsetDateTime.now());

        UserQuota quota = quotaRepository.findById(userId).orElseGet(() -> UserQuota.builder().userId(userId).build());
        if (request.dailyRequestLimit() != null) quota.setDailyRequestLimit(request.dailyRequestLimit());
        if (request.dailyTokenLimit() != null) quota.setDailyTokenLimit(request.dailyTokenLimit());
        if (request.unlimited() != null) quota.setUnlimited(request.unlimited());
        quota.setUpdatedAt(OffsetDateTime.now());
        quotaRepository.save(quota);
        cachedUserAccessService.invalidateAfterCommit(userId);
        return response(user, quota, today());
    }

    @Transactional(readOnly = true)
    public AdminOverviewResponse overview() {
        OffsetDateTime today = today();
        List<AdminOverviewResponse.FeatureUsage> features = usageRepository.summarizeByFeatureSince(today).stream()
                .map(row -> new AdminOverviewResponse.FeatureUsage(
                        String.valueOf(row[0]), number(row[1]), number(row[2]), number(row[3])))
                .toList();
        return new AdminOverviewResponse(userRepository.count(), userRepository.countByStatus(UserStatus.ACTIVE),
                value(usageRepository.sumAllRequestsSince(today)), value(usageRepository.sumAllTokensSince(today)),
                value(usageRepository.sumAllCostSince(today)), features);
    }

    private AdminUserResponse response(User user, UserQuota quota, OffsetDateTime today) {
        return new AdminUserResponse(user.getId().toString(), user.getUsername(), user.getEmail(), user.getRole().name(),
                user.getStatus().name(), Boolean.TRUE.equals(user.getEmailVerified()),
                quota.getDailyRequestLimit(), quota.getDailyTokenLimit(), Boolean.TRUE.equals(quota.getUnlimited()),
                value(usageRepository.sumRequestsSince(user.getId(), today)),
                value(usageRepository.sumTokensSince(user.getId(), today)), user.getCreatedAt().toString(),
                user.getLastLoginAt() == null ? null : user.getLastLoginAt().toString());
    }

    private OffsetDateTime today() {
        ZoneId zone = ZoneId.systemDefault();
        return LocalDate.now(zone).atStartOfDay(zone).toOffsetDateTime();
    }
    private long value(Long value) { return value == null ? 0 : value; }
    private long number(Object value) { return value instanceof Number number ? number.longValue() : 0; }
}
