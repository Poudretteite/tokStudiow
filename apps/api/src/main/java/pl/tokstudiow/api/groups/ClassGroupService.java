package pl.tokstudiow.api.groups;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tokstudiow.api.audit.AuditAction;
import pl.tokstudiow.api.audit.AuditService;
import pl.tokstudiow.api.common.BusinessRuleException;
import pl.tokstudiow.api.common.ConflictException;
import pl.tokstudiow.api.common.CurrentUser;
import pl.tokstudiow.api.common.NotFoundException;
import pl.tokstudiow.api.people.CurrentDeanOffice;
import pl.tokstudiow.api.people.LecturerProfile;
import pl.tokstudiow.api.people.LecturerProfileRepository;
import pl.tokstudiow.api.studies.Study;
import pl.tokstudiow.api.studies.StudyRepository;
import pl.tokstudiow.api.studies.StudyStatus;
import pl.tokstudiow.api.university.AcademicTerm;
import pl.tokstudiow.api.university.AcademicTermRepository;
import pl.tokstudiow.api.university.CourseComponent;
import pl.tokstudiow.api.university.CourseComponentRepository;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ClassGroupService {

    private final ClassGroupRepository groups;
    private final GroupEnrollmentRepository enrollments;
    private final CourseComponentRepository components;
    private final AcademicTermRepository terms;
    private final LecturerProfileRepository lecturers;
    private final StudyRepository studies;
    private final CurrentDeanOffice deanOffice;
    private final AuditService audit;

    public ClassGroupService(ClassGroupRepository groups,
                             GroupEnrollmentRepository enrollments,
                             CourseComponentRepository components,
                             AcademicTermRepository terms,
                             LecturerProfileRepository lecturers,
                             StudyRepository studies,
                             CurrentDeanOffice deanOffice,
                             AuditService audit) {
        this.groups = groups;
        this.enrollments = enrollments;
        this.components = components;
        this.terms = terms;
        this.lecturers = lecturers;
        this.studies = studies;
        this.deanOffice = deanOffice;
        this.audit = audit;
    }

    // dziekanat: tworzenie grup

    @Transactional
    public ClassGroupResponse create(CreateClassGroupRequest request) {
        UUID facultyId = deanOffice.facultyId();
        CourseComponent component = components.findById(request.courseComponentId())
                .filter(c -> c.getCourse().getFieldOfStudy().getFaculty().getId().equals(facultyId))
                .orElseThrow(() -> new NotFoundException("Course component not found"));
        AcademicTerm term = terms.findById(request.academicTermId())
                .orElseThrow(() -> new NotFoundException("Academic term not found"));
        LecturerProfile lecturer = lecturers.findById(request.lecturerId())
                .orElseThrow(() -> new NotFoundException("Lecturer not found"));

        if (groups.existsByCourseComponentIdAndAcademicTermIdAndGroupNumber(
                component.getId(), term.getId(), request.groupNumber())) {
            throw new ConflictException("Group with this number already exists");
        }

        ClassGroup group = groups.save(new ClassGroup(
                component, term, lecturer, request.groupNumber(), request.capacity()));

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("courseComponentId", component.getId().toString());
        snapshot.put("academicTermId", term.getId().toString());
        snapshot.put("lecturerId", lecturer.getUserId().toString());
        snapshot.put("groupNumber", group.getGroupNumber());
        snapshot.put("capacity", group.getCapacity());
        audit.record("class_group", group.getId(), AuditAction.CREATE, null, snapshot);

        return ClassGroupResponse.from(group);
    }

    // dziekanat: zapisy

    @Transactional
    public RosterEntryResponse enroll(UUID groupId, EnrollRequest request) {
        UUID facultyId = deanOffice.facultyId();
        ClassGroup group = lockedGroup(groupId, facultyId);
        return enrollOne(group, studyInFaculty(request.studyId(), facultyId));
    }

    /**
     * Zapisuje wielu studentów naraz, atomowo: jeden niepoprawny wpis (zły kierunek,
     * urlop, brak miejsc) wycofuje cały zapis, więc lista nigdy nie zostaje „w połowie”.
     */
    @Transactional
    public List<RosterEntryResponse> enrollBatch(UUID groupId, BatchEnrollRequest request) {
        UUID facultyId = deanOffice.facultyId();
        ClassGroup group = lockedGroup(groupId, facultyId);

        if (new HashSet<>(request.studyIds()).size() != request.studyIds().size()) {
            throw new BusinessRuleException("Each student may appear only once in the request");
        }
        if (group.getCapacity() != null
                && enrollments.countByClassGroupId(group.getId()) + request.studyIds().size() > group.getCapacity()) {
            throw new ConflictException("Group is full");
        }
        return request.studyIds().stream()
                .map(studyId -> enrollOne(group, studyInFaculty(studyId, facultyId)))
                .toList();
    }

    /** Blokada wiersza grupy: dwa równoczesne zapisy na ostatnie miejsce wykonają się po kolei. */
    private ClassGroup lockedGroup(UUID groupId, UUID facultyId) {
        return groups.findByIdForUpdate(groupId)
                .filter(g -> g.facultyId().equals(facultyId))
                .orElseThrow(() -> new NotFoundException("Group not found"));
    }

    private Study studyInFaculty(UUID studyId, UUID facultyId) {
        return studies.findById(studyId)
                .filter(s -> s.getFieldOfStudy().getFaculty().getId().equals(facultyId))
                .orElseThrow(() -> new NotFoundException("Study not found"));
    }

    private RosterEntryResponse enrollOne(ClassGroup group, Study study) {
        if (study.getStatus() != StudyStatus.ACTIVE) {
            throw new BusinessRuleException("Only active students can be enrolled");
        }
        if (!study.getFieldOfStudy().getId().equals(group.fieldOfStudyId())) {
            throw new BusinessRuleException("Course does not belong to the student's field of study");
        }
        if (enrollments.existsByClassGroupIdAndStudyId(group.getId(), study.getId())) {
            throw new ConflictException("Student is already enrolled in this group");
        }
        if (enrollments.existsInComponentAndTerm(
                study.getId(), group.getCourseComponent().getId(), group.getAcademicTerm().getId())) {
            throw new ConflictException("Student is already enrolled in another group of this course component");
        }
        if (group.getCapacity() != null && enrollments.countByClassGroupId(group.getId()) >= group.getCapacity()) {
            throw new ConflictException("Group is full");
        }

        GroupEnrollment enrollment = enrollments.save(new GroupEnrollment(group, study));

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("classGroupId", group.getId().toString());
        snapshot.put("studyId", study.getId().toString());
        audit.record("group_enrollment", enrollment.getId(), AuditAction.CREATE, null, snapshot);

        return RosterEntryResponse.from(enrollment);
    }

    // odczyt

    /** Grupy wydziału w semestrze, z liczbą zapisanych (jedno zapytanie na liczniki). */
    @Transactional(readOnly = true)
    public List<FacultyGroupResponse> listForMyFaculty(UUID academicTermId) {
        List<ClassGroup> found = groups.findForFaculty(deanOffice.facultyId(), academicTermId);
        Map<UUID, Long> counts = new HashMap<>();
        if (!found.isEmpty()) {
            for (Object[] row : enrollments.countByClassGroupIds(found.stream().map(ClassGroup::getId).toList())) {
                counts.put((UUID) row[0], (Long) row[1]);
            }
        }
        return found.stream()
                .map(g -> new FacultyGroupResponse(ClassGroupResponse.from(g), counts.getOrDefault(g.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public ClassGroupResponse getForMyFaculty(UUID groupId) {
        return groups.findDetailed(groupId)
                .filter(g -> g.facultyId().equals(deanOffice.facultyId()))
                .map(ClassGroupResponse::from)
                .orElseThrow(() -> new NotFoundException("Group not found"));
    }

    @Transactional(readOnly = true)
    public List<RosterEntryResponse> roster(UUID groupId) {
        groups.findDetailed(groupId)
                .filter(this::canViewRoster)
                .orElseThrow(() -> new NotFoundException("Group not found"));
        return enrollments.findRoster(groupId).stream().map(RosterEntryResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ClassGroupResponse> myGroupsAsLecturer() {
        return groups.findForLecturer(CurrentUser.id()).stream().map(ClassGroupResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ClassGroupResponse> myGroupsAsStudent() {
        return groups.findForStudent(CurrentUser.id()).stream().map(ClassGroupResponse::from).toList();
    }

    /** Listę studentów widzi prowadzący grupy, dziekanat wydziału i admin. Brak dostępu = 404. */
    private boolean canViewRoster(ClassGroup group) {
        return switch (CurrentUser.role()) {
            case ADMIN -> true;
            case LECTURER -> group.getLecturer().getUserId().equals(CurrentUser.id());
            case DEAN_OFFICE -> group.facultyId().equals(deanOffice.facultyId());
            case STUDENT -> false;
        };
    }
}
