package pl.tokstudiow.api.audit;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AuditService {

    private final JdbcClient jdbc;
    private final ObjectMapper json;

    public AuditService(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /**
     * Zapisuje zdarzenie w dzienniku. Wymaga trwającej transakcji,
     * więc wpis i zmiana danych zapisują się razem albo wcale.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String entityType, UUID entityId, AuditAction action,
                       @Nullable Map<String, ?> oldValue, @Nullable Map<String, ?> newValue) {
        jdbc.sql("""
                INSERT INTO audit_log (actor_id, entity_type, entity_id, action, old_value, new_value)
                VALUES (:actorId, :entityType, :entityId, :action,
                        CAST(:oldValue AS jsonb), CAST(:newValue AS jsonb))
                """)
                .param("actorId", currentActorId())
                .param("entityType", entityType)
                .param("entityId", entityId)
                .param("action", action.name())
                .param("oldValue", toJson(oldValue))
                .param("newValue", toJson(newValue))
                .update();
    }

    @Transactional(readOnly = true)
    public List<AuditEntryResponse> historyOf(String entityType, UUID entityId) {
        return jdbc.sql("""
                SELECT id, actor_id, entity_type, entity_id, action,
                       CAST(old_value AS text) AS old_value,
                       CAST(new_value AS text) AS new_value,
                       created_at
                FROM audit_log
                WHERE entity_type = :entityType AND entity_id = :entityId
                ORDER BY created_at DESC
                LIMIT 100
                """)
                .param("entityType", entityType)
                .param("entityId", entityId)
                .query(this::toEntry)
                .list();
    }

    /**
     * Najnowsze wpisy, opcjonalnie dla jednego typu obiektu lub jednej osoby.
     * Stronicowanie kursorem (created_at, id): stabilne także dla wpisów z tej samej transakcji,
     * które mają identyczny czas.
     */
    @Transactional(readOnly = true)
    public List<AuditEntryResponse> recent(@Nullable String entityType, @Nullable UUID actorId,
                                           @Nullable Instant beforeCreatedAt, @Nullable UUID beforeId, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT id, actor_id, entity_type, entity_id, action,
                       CAST(old_value AS text) AS old_value,
                       CAST(new_value AS text) AS new_value,
                       created_at
                FROM audit_log
                WHERE TRUE
                """);
        Map<String, Object> params = new HashMap<>();
        if (entityType != null) {
            sql.append(" AND entity_type = :entityType");
            params.put("entityType", entityType);
        }
        if (actorId != null) {
            sql.append(" AND actor_id = :actorId");
            params.put("actorId", actorId);
        }
        if (beforeCreatedAt != null && beforeId != null) {
            sql.append(" AND (created_at, id) < (:beforeCreatedAt, :beforeId)");
            params.put("beforeCreatedAt", OffsetDateTime.ofInstant(beforeCreatedAt, ZoneOffset.UTC));
            params.put("beforeId", beforeId);
        }
        sql.append(" ORDER BY created_at DESC, id DESC LIMIT :limit");
        params.put("limit", limit);

        return jdbc.sql(sql.toString()).params(params).query(this::toEntry).list();
    }

    private AuditEntryResponse toEntry(ResultSet rs, int rowNum) throws SQLException {
        return new AuditEntryResponse(
                rs.getObject("id", UUID.class),
                rs.getObject("actor_id", UUID.class),
                rs.getString("entity_type"),
                rs.getObject("entity_id", UUID.class),
                AuditAction.valueOf(rs.getString("action")),
                fromJson(rs.getString("old_value")),
                fromJson(rs.getString("new_value")),
                rs.getObject("created_at", OffsetDateTime.class).toInstant());
    }

    private static @Nullable UUID currentActorId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            return UUID.fromString(jwtAuthentication.getName());
        }
        return null;
    }

    private @Nullable String toJson(@Nullable Map<String, ?> value) {
        return value == null ? null : json.writeValueAsString(value);
    }

    private @Nullable JsonNode fromJson(@Nullable String value) {
        return value == null ? null : json.readTree(value);
    }
}
