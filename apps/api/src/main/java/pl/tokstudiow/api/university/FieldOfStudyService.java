package pl.tokstudiow.api.university;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tokstudiow.api.audit.AuditAction;
import pl.tokstudiow.api.audit.AuditService;
import pl.tokstudiow.api.common.ConflictException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class FieldOfStudyService {

    private static final String AUDIT_ENTITY = "field_of_study";

    private final FieldOfStudyRepository fields;
    private final FacultyService facultyService;
    private final AuditService audit;

    public FieldOfStudyService(FieldOfStudyRepository fields, FacultyService facultyService, AuditService audit) {
        this.fields = fields;
        this.facultyService = facultyService;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<FieldOfStudyResponse> listForFaculty(UUID facultyId) {
        facultyService.find(facultyId); // 404, jeśli wydział nie istnieje
        return fields.findByFacultyIdOrderByNameAscLevelAscModeAsc(facultyId).stream()
                .map(FieldOfStudyResponse::from)
                .toList();
    }

    @Transactional
    public FieldOfStudyResponse create(FieldOfStudyRequest request) {
        Faculty faculty = facultyService.find(request.facultyId());
        String name = request.name().trim();
        if (fields.existsByFacultyIdAndNameIgnoreCaseAndLevelAndMode(
                faculty.getId(), name, request.level(), request.mode())) {
            throw new ConflictException("This field of study already exists in the faculty");
        }

        FieldOfStudy field = fields.save(new FieldOfStudy(
                faculty, name, request.level(), request.mode(), request.semestersCount()));
        audit.record(AUDIT_ENTITY, field.getId(), AuditAction.CREATE, null, snapshot(field));
        return FieldOfStudyResponse.from(field);
    }

    private static Map<String, Object> snapshot(FieldOfStudy field) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("facultyId", field.getFaculty().getId().toString());
        snapshot.put("name", field.getName());
        snapshot.put("level", field.getLevel().name());
        snapshot.put("mode", field.getMode().name());
        snapshot.put("semestersCount", field.getSemestersCount());
        return snapshot;
    }
}