package com.omnistudy.agentscope;

import com.fasterxml.jackson.core.type.TypeReference;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.State;
import io.agentscope.core.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class PostgresAgentStateStore implements AgentStateStore {
    private static final String ANONYMOUS = "__anonymous__";
    private final JdbcTemplate jdbc;

    @Override
    public void save(String userId, String sessionId, String key, State value) {
        upsert(userId, sessionId, key, JsonUtils.getJsonCodec().toJson(value), false);
    }

    @Override
    public void save(String userId, String sessionId, String key, List<? extends State> values) {
        upsert(userId, sessionId, key, JsonUtils.getJsonCodec().toJson(values), true);
    }

    private void upsert(String userId, String sessionId, String key, String json, boolean list) {
        validate(sessionId, key);
        jdbc.update("""
                INSERT INTO agent_scope_states(user_key, session_id, state_key, state_value, list_value)
                VALUES (?, ?, ?, CAST(? AS jsonb), ?)
                ON CONFLICT (user_key, session_id, state_key) DO UPDATE
                SET state_value = EXCLUDED.state_value, list_value = EXCLUDED.list_value, updated_at = NOW()
                """, normalizeUser(userId), sessionId, key, json, list);
    }

    @Override
    public <T extends State> Optional<T> get(String userId, String sessionId, String key, Class<T> type) {
        try {
            String json = jdbc.queryForObject("""
                    SELECT state_value::text FROM agent_scope_states
                    WHERE user_key = ? AND session_id = ? AND state_key = ? AND list_value = FALSE
                    """, String.class, normalizeUser(userId), sessionId, key);
            return Optional.of(JsonUtils.getJsonCodec().fromJson(json, type));
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }

    @Override
    public <T extends State> List<T> getList(String userId, String sessionId, String key, Class<T> itemType) {
        try {
            String json = jdbc.queryForObject("""
                    SELECT state_value::text FROM agent_scope_states
                    WHERE user_key = ? AND session_id = ? AND state_key = ? AND list_value = TRUE
                    """, String.class, normalizeUser(userId), sessionId, key);
            List<Object> values = JsonUtils.getJsonCodec().fromJson(json, new TypeReference<>() {});
            return values.stream().map(value -> JsonUtils.getJsonCodec().convertValue(value, itemType)).toList();
        } catch (EmptyResultDataAccessException ignored) {
            return List.of();
        }
    }

    @Override
    public boolean exists(String userId, String sessionId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM agent_scope_states WHERE user_key = ? AND session_id = ?
                """, Integer.class, normalizeUser(userId), sessionId);
        return count != null && count > 0;
    }

    @Override
    public void delete(String userId, String sessionId) {
        jdbc.update("DELETE FROM agent_scope_states WHERE user_key = ? AND session_id = ?",
                normalizeUser(userId), sessionId);
    }

    @Override
    public void delete(String userId, String sessionId, String key) {
        jdbc.update("DELETE FROM agent_scope_states WHERE user_key = ? AND session_id = ? AND state_key = ?",
                normalizeUser(userId), sessionId, key);
    }

    @Override
    public Set<String> listSessionIds(String userId) {
        return Set.copyOf(jdbc.queryForList("""
                SELECT DISTINCT session_id FROM agent_scope_states WHERE user_key = ?
                """, String.class, normalizeUser(userId)));
    }

    private String normalizeUser(String userId) {
        return userId == null || userId.isBlank() ? ANONYMOUS : userId;
    }

    private void validate(String sessionId, String key) {
        if (sessionId == null || sessionId.isBlank()) throw new IllegalArgumentException("sessionId is required");
        if (key == null || key.isBlank()) throw new IllegalArgumentException("state key is required");
        if (sessionId.length() > 120 || key.length() > 120) throw new IllegalArgumentException("state key is too long");
    }
}
