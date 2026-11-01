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

        // Blokada wiersza grupy: dwa równoczesne zapisy na ostatnie miejsce wykonają się po kolei
        ClassGroup group = groups.findByIdForUpdate(groupId)
                .filter(g -> g.facultyId().equals(facultyId))
                .orElseThrow(() -> new NotFoundException("Group not found"));
        Study study = studies.findById(request.studyId())
                .filter(s -> s.getFieldOfStudy().getFaculty().getId().equals(facultyId))
                .orElseThrow(() -> new NotFoundException("Study not found"));

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