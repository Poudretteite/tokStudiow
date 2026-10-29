package pl.tokstudiow.api.audit;

import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record AuditEntryResponse(
        UUID id,
        @Nullable UUID actorId,
        String entityType,
        UUID entityId,
        AuditAction action,
        @Nullable JsonNode oldValue,
        @Nullable JsonNode newValue,
        Instant createdAt
) {
}