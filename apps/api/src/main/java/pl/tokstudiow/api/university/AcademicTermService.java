package pl.tokstudiow.api.university;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tokstudiow.api.audit.AuditAction;
import pl.tokstudiow.api.audit.AuditService;
import pl.tokstudiow.api.common.BusinessRuleException;
import pl.tokstudiow.api.common.ConflictException;
import pl.tokstudiow.api.common.NotFoundException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class AcademicTermService {

    private static final String AUDIT_ENTITY = "academic_term";

    private final AcademicTermRepository terms;
    private final AuditService audit;

    public AcademicTermService(AcademicTermRepository terms, AuditService audit) {
        this.terms = terms;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<AcademicTermResponse> list() {
        return terms.findAllByOrderByStartDateDesc().stream().map(AcademicTermResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public AcademicTermResponse current() {
        return terms.findByCurrentTermTrue()
                .map(AcademicTermResponse::from)
                .orElseThrow(() -> new NotFoundException("No current academic term"));
    }

    @Transactional
    public AcademicTermResponse create(AcademicTermRequest request) {
        if (!request.endDate().isAfter(request.startDate())) {
            throw new BusinessRuleException("End date must be after start date");
        }
        String name = request.name().trim();
        if (terms.existsByNameIgnoreCase(name)) {
            throw new ConflictException("Academic term with this name already exists");
        }
        if (terms.existsOverlapping(request.startDate(), request.endDate())) {
            throw new ConflictException("Academic term overlaps an existing term");
        }

        AcademicTerm term = terms.save(new AcademicTerm(name, request.type(), request.startDate(), request.endDate()));
        audit.record(AUDIT_ENTITY, term.getId(), AuditAction.CREATE, null, snapshot(term));
        return AcademicTermResponse.from(term);
    }

    /**
     * Ustawia semestr jako bieżący. Poprzedni bieżący przestaje nim być
     * w tej samej transakcji – nigdy nie ma dwóch ani chwilowo żadnego.
     */
    @Transactional
    public AcademicTermResponse makeCurrent(UUID id) {
        AcademicTerm term = terms.findById(id)
                .orElseThrow(() -> new NotFoundException("Academic term not found"));
        if (term.isCurrentTerm()) {
            return AcademicTermResponse.from(term);
        }

        Optional<AcademicTerm> previous = terms.findByCurrentTermTrue();
        Optional<Map<String, Object>> previousBefore = previous.map(AcademicTermService::snapshot);

        terms.clearCurrent();
        previous.ifPresent(AcademicTerm::unmarkCurrent);

        Map<String, Object> before = snapshot(term);
        term.markCurrent();

        previous.ifPresent(p -> audit.record(
                AUDIT_ENTITY, p.getId(), AuditAction.UPDATE, previousBefore.orElseThrow(), snapshot(p)));
        audit.record(AUDIT_ENTITY, term.getId(), AuditAction.UPDATE, before, snapshot(term));
        return AcademicTermResponse.from(term);
    }

    private static Map<String, Object> snapshot(AcademicTerm term) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("name", term.getName());
        snapshot.put("type", term.getType().name());
        snapshot.put("startDate", term.getStartDate().toString());
        snapshot.put("endDate", term.getEndDate().toString());
        snapshot.put("current", term.isCurrentTerm());
        return snapshot;
    }
}