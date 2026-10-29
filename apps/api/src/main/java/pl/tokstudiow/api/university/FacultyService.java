package pl.tokstudiow.api.university;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tokstudiow.api.audit.AuditAction;
import pl.tokstudiow.api.audit.AuditService;
import pl.tokstudiow.api.common.ConflictException;
import pl.tokstudiow.api.common.NotFoundException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class FacultyService {

    private static final String AUDIT_ENTITY = "faculty";

    private final FacultyRepository faculties;
    private final AuditService audit;

    public FacultyService(FacultyRepository faculties, AuditService audit) {
        this.faculties = faculties;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<FacultyResponse> list() {
        return faculties.findAll(Sort.by("name")).stream().map(FacultyResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public FacultyResponse get(UUID id) {
        return FacultyResponse.from(find(id));
    }

    @Transactional
    public FacultyResponse create(FacultyRequest request) {
        String name = request.name().trim();
        String shortName = request.shortName().trim();
        if (faculties.existsByNameIgnoreCaseOrShortNameIgnoreCase(name, shortName)) {
            throw new ConflictException("Faculty with this name or short name already exists");
        }

        Faculty faculty = faculties.save(new Faculty(name, shortName));
        audit.record(AUDIT_ENTITY, faculty.getId(), AuditAction.CREATE, null, snapshot(faculty));
        return FacultyResponse.from(faculty);
    }

    @Transactional
    public FacultyResponse update(UUID id, FacultyRequest request) {
        Faculty faculty = find(id);
        String name = request.name().trim();
        String shortName = request.shortName().trim();
        if (faculties.existsOtherWithNameOrShortName(id, name, shortName)) {
            throw new ConflictException("Faculty with this name or short name already exists");
        }

        Map<String, Object> before = snapshot(faculty);
        faculty.update(name, shortName);
        audit.record(AUDIT_ENTITY, id, AuditAction.UPDATE, before, snapshot(faculty));
        return FacultyResponse.from(faculty);
    }

    Faculty find(UUID id) {
        return faculties.findById(id).orElseThrow(() -> new NotFoundException("Faculty not found"));
    }

    private static Map<String, Object> snapshot(Faculty faculty) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("name", faculty.getName());
        snapshot.put("shortName", faculty.getShortName());
        return snapshot;
    }
}