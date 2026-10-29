package pl.tokstudiow.api.admin;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.tokstudiow.api.audit.AuditEntryResponse;
import pl.tokstudiow.api.audit.AuditService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/audit-log")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAuditController {

    private final AuditService auditService;

    public AdminAuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    public List<AuditEntryResponse> history(@RequestParam String entityType, @RequestParam UUID entityId) {
        return auditService.historyOf(entityType, entityId);
    }
}