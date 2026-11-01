package pl.tokstudiow.api.studies;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tokstudiow.api.audit.AuditAction;
import pl.tokstudiow.api.audit.AuditService;
import pl.tokstudiow.api.common.BusinessRuleException;
import pl.tokstudiow.api.common.ConflictException;
import pl.tokstudiow.api.common.CurrentUser;
import pl.tokstudiow.api.common.NotFoundException;
import pl.tokstudiow.api.people.DeanOfficeProfileRepository;
import pl.tokstudiow.api.people.StudentProfile;
import pl.tokstudiow.api.people.StudentProfileRepository;
import pl.tokstudiow.api.university.FieldOfStudy;
import pl.tokstudiow.api.university.FieldOfStudyRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class StudyService {

    private static final String AUDIT_ENTITY = "study";

    private final StudyRepository studies;
    private final StudentProfileRepository studentProfiles;
    private final FieldOfStudyRepository fields;
    private final DeanOfficeProfileRepository deanOfficeProfiles;
    private final AuditService audit;

    public StudyService(StudyRepository studies,
                        StudentProfileRepository studentProfiles,
                        FieldOfStudyRepository fields,
                        DeanOfficeProfileRepository deanOfficeProfiles,
                        AuditService audit) {
        this.studies = studies;
        this.studentProfiles = studentProfiles;
        this.fields = fields;
        this.deanOfficeProfiles = deanOfficeProfiles;
        this.audit = audit;
    }

    // student

    @Transactional(readOnly = true)
    public List<StudyResponse> myStudies() {
        return studies.findForStudent(CurrentUser.id()).stream().map(StudyResponse::from).toList();
    }

    // odczyt pojedynczego rekordu (wszystkie role)

    @Transactional(readOnly = true)
    public StudyResponse get(UUID id) {
        return studies.findDetailed(id)
                .filter(this::canView)
                .map(StudyResponse::from)
                .orElseThrow(StudyService::notFound);
    }

    /**
     * Czy zalogowany użytkownik może zobaczyć ten przebieg studiów.
     * Brak dostępu jest nieodróżnialny od braku rekordu (404), żeby nie zdradzać, że ID istnieje.
     */
    private boolean canView(Study study) {
        return switch (CurrentUser.role()) {
            case ADMIN -> true;
            case STUDENT -> study.getStudent().getUserId().equals(CurrentUser.id());
            case DEAN_OFFICE -> study.facultyId().equals(myFacultyId());
            case LECTURER -> false;
        };
    }

    // dziekanat

    @Transactional(readOnly = true)
    public List<StudyResponse> listForMyFaculty() {
        return studies.findForFaculty(myFacultyId()).stream().map(StudyResponse::from).toList();
    }

    @Transactional
    public StudyResponse create(CreateStudyRequest request) {
        UUID facultyId = myFacultyId();
        FieldOfStudy field = fields.findById(request.fieldOfStudyId())
                .filter(f -> f.getFaculty().getId().equals(facultyId))
                .orElseThrow(() -> new NotFoundException("Field of study not found"));
        StudentProfile student = studentProfiles.findById(request.studentId())
                .orElseThrow(() -> new NotFoundException("Student not found"));

        requireSemesterWithinField(request.currentSemester(), field);
        if (studies.existsByStudentUserIdAndFieldOfStudyId(student.getUserId(), field.getId())) {
            throw new ConflictException("Student is already enrolled in this field of study");
        }

        Study study = studies.save(new Study(student, field, request.startYear(), request.currentSemester()));
        audit.record(AUDIT_ENTITY, study.getId(), AuditAction.CREATE, null, snapshot(study));
        return StudyResponse.from(study);
    }

    @Transactional
    public StudyResponse update(UUID id, UpdateStudyRequest request) {
        UUID facultyId = myFacultyId();
        Study study = studies.findDetailed(id)
                .filter(s -> s.facultyId().equals(facultyId))
                .orElseThrow(StudyService::notFound);

        requireSemesterWithinField(request.currentSemester(), study.getFieldOfStudy());

        Map<String, Object> before = snapshot(study);
        study.update(request.currentSemester(), request.status());
        audit.record(AUDIT_ENTITY, id, AuditAction.UPDATE, before, snapshot(study));
        return StudyResponse.from(study);
    }

    // pomocnicze

    private UUID myFacultyId() {
        return deanOfficeProfiles.findById(CurrentUser.id())
                .map(p -> p.getFaculty().getId())
                .orElseThrow(() -> new AccessDeniedException("No dean office profile"));
    }

    private static void requireSemesterWithinField(short semester, FieldOfStudy field) {
        if (semester > field.getSemestersCount()) {
            throw new BusinessRuleException("Semester exceeds the length of the field of study");
        }
    }

    private static NotFoundException notFound() {
        return new NotFoundException("Study not found");
    }

    private static Map<String, Object> snapshot(Study study) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("studentId", study.getStudent().getUserId().toString());
        snapshot.put("fieldOfStudyId", study.getFieldOfStudy().getId().toString());
        snapshot.put("startYear", study.getStartYear());
        snapshot.put("currentSemester", study.getCurrentSemester());
        snapshot.put("status", study.getStatus().name());
        return snapshot;
    }
}