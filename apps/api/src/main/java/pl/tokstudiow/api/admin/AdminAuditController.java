package pl.tokstudiow.api.admin;

import org.jspecify.annotations.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.tokstudiow.api.audit.AuditEntryResponse;
import pl.tokstudiow.api.audit.AuditService;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/audit-log")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAuditController {

    private static final int MAX_PAGE = 200;

    private final AuditService auditService;

    public AdminAuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    public List<AuditEntryResponse> history(@RequestParam String entityType, @RequestParam UUID entityId) {
        return auditService.historyOf(entityType, entityId);
    }

    /** Przeglądanie dziennika od najnowszych; kolejna strona: created_at i id ostatniego wpisu. */
    @GetMapping("/recent")
    public List<AuditEntryResponse> recent(@RequestParam(required = false) @Nullable String entityType,
                                           @RequestParam(required = false) @Nullable UUID actorId,
                                           @RequestParam(required = false) @Nullable Instant beforeCreatedAt,
                                           @RequestParam(required = false) @Nullable UUID beforeId,
                                           @RequestParam(defaultValue = "50") int limit) {
        return auditService.recent(entityType, actorId, beforeCreatedAt, beforeId,
                Math.clamp(limit, 1, MAX_PAGE));
    }
}
