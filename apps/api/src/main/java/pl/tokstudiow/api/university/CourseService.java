package pl.tokstudiow.api.university;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tokstudiow.api.audit.AuditAction;
import pl.tokstudiow.api.audit.AuditService;
import pl.tokstudiow.api.common.BusinessRuleException;
import pl.tokstudiow.api.common.ConflictException;
import pl.tokstudiow.api.common.NotFoundException;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class CourseService {

    private static final String AUDIT_ENTITY = "course";

    private final CourseRepository courses;
    private final FieldOfStudyRepository fields;
    private final GradingScaleService gradingScales;
    private final AuditService audit;

    public CourseService(CourseRepository courses,
                         FieldOfStudyRepository fields,
                         GradingScaleService gradingScales,
                         AuditService audit) {
        this.courses = courses;
        this.fields = fields;
        this.gradingScales = gradingScales;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<CourseResponse> listForFieldOfStudy(UUID fieldOfStudyId) {
        if (!fields.existsById(fieldOfStudyId)) {
            throw new NotFoundException("Field of study not found");
        }
        return courses.findByFieldOfStudyWithComponents(fieldOfStudyId).stream()
                .map(CourseResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CourseResponse get(UUID id) {
        return courses.findWithComponents(id)
                .map(CourseResponse::from)
                .orElseThrow(() -> new NotFoundException("Course not found"));
    }

    @Transactional
    public CourseResponse create(CourseRequest request) {
        FieldOfStudy field = fields.findById(request.fieldOfStudyId())
                .orElseThrow(() -> new NotFoundException("Field of study not found"));

        if (request.semesterNumber() > field.getSemestersCount()) {
            throw new BusinessRuleException("Semester number exceeds the length of the field of study");
        }
        Set<ComponentType> seen = EnumSet.noneOf(ComponentType.class);
        for (CourseComponentRequest component : request.components()) {
            if (!seen.add(component.type())) {
                throw new BusinessRuleException("Each component type may appear only once in a course");
            }
        }
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (courses.existsByCodeIgnoreCase(code)) {
            throw new ConflictException("Course with this code already exists");
        }

        Course course = new Course(field, code, request.name().trim(), request.ects(), request.semesterNumber());
        for (CourseComponentRequest component : request.components()) {
            course.addComponent(component.type(), component.hours(), gradingScales.find(component.gradingScaleId()));
        }
        courses.save(course);

        audit.record(AUDIT_ENTITY, course.getId(), AuditAction.CREATE, null, snapshot(course));
        return CourseResponse.from(course);
    }

    private static Map<String, Object> snapshot(Course course) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("fieldOfStudyId", course.getFieldOfStudy().getId().toString());
        snapshot.put("code", course.getCode());
        snapshot.put("name", course.getName());
        snapshot.put("ects", course.getEcts());
        snapshot.put("semesterNumber", course.getSemesterNumber());
        snapshot.put("components", course.getComponents().stream()
                .map(c -> Map.of(
                        "type", c.getType().name(),
                        "hours", c.getHours(),
                        "gradingScaleId", c.getGradingScale().getId().toString()))
                .toList());
        return snapshot;
    }
}