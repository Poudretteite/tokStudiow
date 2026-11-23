package pl.tokstudiow.api.grades;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tokstudiow.api.audit.AuditAction;
import pl.tokstudiow.api.audit.AuditService;
import pl.tokstudiow.api.common.BusinessRuleException;
import pl.tokstudiow.api.common.ConflictException;
import pl.tokstudiow.api.common.CurrentUser;
import pl.tokstudiow.api.common.NotFoundException;
import pl.tokstudiow.api.groups.ClassGroup;
import pl.tokstudiow.api.groups.ClassGroupRepository;
import pl.tokstudiow.api.groups.GroupEnrollment;
import pl.tokstudiow.api.groups.GroupEnrollmentRepository;
import pl.tokstudiow.api.people.CurrentDeanOffice;
import pl.tokstudiow.api.university.GradeValue;
import pl.tokstudiow.api.university.GradeValueRepository;
import pl.tokstudiow.api.university.GradingScale;
import pl.tokstudiow.api.university.GradingScaleRepository;
import pl.tokstudiow.api.university.GradingScaleType;
import pl.tokstudiow.api.user.User;
import pl.tokstudiow.api.user.UserRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class GradeService {

    private static final BigDecimal MAX_TOTAL_WEIGHT = new BigDecimal("100");
    private static final String AUDIT_ENTITY = "partial_grade";

    private final ClassGroupRepository groups;
    private final GroupEnrollmentRepository enrollments;
    private final AssessmentItemRepository items;
    private final PartialGradeRepository grades;
    private final FinalGradeRepository finalGrades;
    private final GradeValueRepository gradeValues;
    private final GradingScaleRepository scales;
    private final UserRepository users;
    private final CurrentDeanOffice deanOffice;
    private final AuditService audit;
    private final Clock clock;

    public GradeService(ClassGroupRepository groups,
                        GroupEnrollmentRepository enrollments,
                        AssessmentItemRepository items,
                        PartialGradeRepository grades,
                        FinalGradeRepository finalGrades,
                        GradeValueRepository gradeValues,
                        GradingScaleRepository scales,
                        UserRepository users,
                        CurrentDeanOffice deanOffice,
                        AuditService audit,
                        Clock clock) {
        this.groups = groups;
        this.enrollments = enrollments;
        this.items = items;
        this.grades = grades;
        this.finalGrades = finalGrades;
        this.gradeValues = gradeValues;
        this.scales = scales;
        this.users = users;
        this.deanOffice = deanOffice;
        this.audit = audit;
        this.clock = clock;
    }

    // elementy zaliczenia

    /**
     * Dodaje element zaliczenia. Rodzaj oceniania wybiera prowadzący (domyślnie jak forma zajęć):
     * ocena wlicza się do średniej ważonej, zal/nzal jest warunkiem zaliczenia i nie ma wagi.
     */
    @Transactional
    public AssessmentItemResponse addItem(UUID groupId, AssessmentItemRequest request) {
        // Blokada grupy: dwa równoczesne dodania nie przekroczą razem 100%
        ClassGroup group = groups.findByIdForUpdate(groupId)
                .filter(this::isMyGroup)
                .orElseThrow(GradeService::groupNotFound);

        GradingScaleType type = request.gradingType() != null
                ? request.gradingType()
                : group.getCourseComponent().getGradingScale().getType();
        GradingScale scale = scales.findFirstByType(type)
                .orElseThrow(() -> new NotFoundException("Grading scale not found"));

        BigDecimal weight;
        if (type == GradingScaleType.PASS_FAIL) {
            weight = BigDecimal.ZERO;
        } else {
            if (request.weightPercent() == null || request.weightPercent().signum() <= 0) {
                throw new BusinessRuleException("Graded assessment items need a weight greater than 0");
            }
            weight = request.weightPercent();
            BigDecimal current = items.sumWeights(groupId);
            BigDecimal total = (current == null ? BigDecimal.ZERO : current).add(weight);
            if (total.compareTo(MAX_TOTAL_WEIGHT) > 0) {
                throw new BusinessRuleException("Total weight of assessment items cannot exceed 100%");
            }
        }

        short orderIndex = (short) (items.countByClassGroupId(groupId) + 1);
        AssessmentItem item = items.save(new AssessmentItem(
                group, scale, request.name().trim(), weight, orderIndex, request.plannedDate()));

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("classGroupId", groupId.toString());
        snapshot.put("name", item.getName());
        snapshot.put("gradingType", type.name());
        snapshot.put("weightPercent", item.getWeightPercent());
        snapshot.put("orderIndex", item.getOrderIndex());
        audit.record("assessment_item", item.getId(), AuditAction.CREATE, null, snapshot);

        return AssessmentItemResponse.from(item);
    }

    @Transactional(readOnly = true)
    public List<AssessmentItemResponse> listItems(UUID groupId) {
        groups.findDetailed(groupId)
                .filter(this::canViewGroup)
                .orElseThrow(GradeService::groupNotFound);
        return items.findByClassGroupIdOrderByOrderIndex(groupId).stream()
                .map(AssessmentItemResponse::from)
                .toList();
    }

    // oceny cząstkowe: wystawianie

    /** Jedna ocena – szczególny przypadek wystawienia wielu ocen naraz. */
    @Transactional
    public PartialGradeResponse issue(UUID groupId, PartialGradeRequest request) {
        return issueBatch(groupId, new PartialGradeBatchRequest(
                request.assessmentItemId(),
                List.of(new BatchGradeEntry(request.enrollmentId(), request.gradeValueId()))))
                .getFirst();
    }

    /**
     * Wystawia oceny z jednego elementu zaliczenia wielu studentom naraz.
     * Wszystko albo nic: jeden błędny wpis wycofuje całe żądanie.
     * Ocena wystawiona ponownie z tego samego elementu to kolejna próba (poprawa zaliczenia).
     */
    @Transactional
    public List<PartialGradeResponse> issueBatch(UUID groupId, PartialGradeBatchRequest request) {
        myGroup(groupId);
        AssessmentItem item = items.findById(request.assessmentItemId())
                .filter(i -> i.getClassGroup().getId().equals(groupId))
                .orElseThrow(() -> new NotFoundException("Assessment item not found"));

        Set<UUID> seen = new HashSet<>();
        for (BatchGradeEntry entry : request.entries()) {
            if (!seen.add(entry.enrollmentId())) {
                throw new BusinessRuleException("Each student may appear only once in the request");
            }
        }

        // Zapisy tylko z tej grupy – identyfikator spoza niej kończy się 404 dla całego żądania
        Map<UUID, GroupEnrollment> groupEnrollments = enrollments.findRoster(groupId).stream()
                .collect(Collectors.toMap(GroupEnrollment::getId, Function.identity()));
        User issuer = users.getReferenceById(CurrentUser.id());

        return request.entries().stream().map(entry -> {
            GroupEnrollment enrollment = groupEnrollments.get(entry.enrollmentId());
            if (enrollment == null) {
                throw new NotFoundException("Enrollment not found");
            }
            GradeValue value = gradeValueForItem(entry.gradeValueId(), item);
            return PartialGradeResponse.from(issueOne(enrollment, item, value, issuer));
        }).toList();
    }

    // oceny cząstkowe: korekta błędnego wpisu

    /**
     * Korekta pomyłki przez prowadzącego. Zmienia istniejącą ocenę, nie tworzy nowej próby.
     * Ocena wpisana do końca sesji: korekta do końca sesji + 2 dni; wpisana później (sesja
     * poprawkowa): do końca sesji poprawkowej + 2 dni. Po terminie poprawia już tylko dziekanat.
     * Prowadzący nie podaje powodu; kto, kiedy i jaka zmiana trafia do audytu automatycznie.
     * Po zatwierdzeniu oceny końcowej oceny cząstkowe są zablokowane – wtedy poprawia się ocenę końcową.
     */
    @Transactional
    public PartialGradeResponse correct(UUID groupId, UUID gradeId, LecturerCorrectionRequest request) {
        ClassGroup group = myGroup(groupId);
        PartialGrade grade = grades.findById(gradeId)
                .filter(g -> g.getGroupEnrollment().getClassGroup().getId().equals(groupId))
                .orElseThrow(GradeService::gradeNotFound);

        // ocena wpisana po końcu sesji należy do sesji poprawkowej i ma jej termin korekt
        var term = group.getAcademicTerm();
        boolean retakePeriod = term.isRetakePeriod(LocalDate.ofInstant(grade.getIssuedAt(), clock.getZone()));
        if (!term.allowsLecturerCorrection(LocalDate.now(clock), retakePeriod)) {
            throw new ConflictException("Correction period has ended");
        }
        if (finalGrades.existsByGroupEnrollmentIdAndStatus(
                grade.getGroupEnrollment().getId(), FinalGradeStatus.APPROVED)) {
            throw new ConflictException("Partial grades are locked after final grade approval");
        }
        return applyCorrection(grade, request.gradeValueId(), null);
    }

    /**
     * Korekta przez dziekanat – także po terminie korekt i po zatwierdzeniu oceny końcowej.
     * Zawsze z komentarzem, bo zmienia ocenę bez udziału prowadzącego.
     */
    @Transactional
    public PartialGradeResponse correctAsDeanOffice(UUID gradeId, CorrectGradeRequest request) {
        PartialGrade grade = grades.findById(gradeId)
                .filter(g -> g.getGroupEnrollment().getClassGroup().facultyId().equals(deanOffice.facultyId()))
                .orElseThrow(GradeService::gradeNotFound);
        return applyCorrection(grade, request.gradeValueId(), request.reason());
    }

    private PartialGradeResponse applyCorrection(PartialGrade grade, UUID gradeValueId, @Nullable String reason) {
        GradeValue value = gradeValueForItem(gradeValueId, grade.getAssessmentItem());
        if (value.getId().equals(grade.getGradeValue().getId())) {
            return PartialGradeResponse.from(grade);
        }

        Map<String, Object> before = snapshot(grade);
        grade.correct(value);
        Map<String, Object> after = snapshot(grade);
        after.put("correctedBy", CurrentUser.id().toString());
        if (reason != null && !reason.isBlank()) {
            after.put("reason", reason.trim());
        }
        audit.record(AUDIT_ENTITY, grade.getId(), AuditAction.UPDATE, before, after);
        return PartialGradeResponse.from(grade);
    }

    // odczyt

    @Transactional(readOnly = true)
    public List<PartialGradeResponse> gradebook(UUID groupId) {
        myGroup(groupId);
        return grades.findForGroup(groupId).stream().map(PartialGradeResponse::from).toList();
    }

    /** Oceny cząstkowe grupy z wydziału pracownika dziekanatu – do korekty po sesji. */
    @Transactional(readOnly = true)
    public List<PartialGradeResponse> gradebookForDeanOffice(UUID groupId) {
        groups.findDetailed(groupId)
                .filter(g -> g.facultyId().equals(deanOffice.facultyId()))
                .orElseThrow(GradeService::groupNotFound);
        return grades.findForGroup(groupId).stream().map(PartialGradeResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<PartialGradeResponse> myGrades(UUID groupId) {
        UUID studentId = CurrentUser.id();
        if (!enrollments.existsByClassGroupIdAndStudyStudentUserId(groupId, studentId)) {
            throw groupNotFound();
        }
        return grades.findForStudentInGroup(groupId, studentId).stream()
                .map(PartialGradeResponse::from)
                .toList();
    }

    // pomocnicze

    /** Zapisuje jedną ocenę jako kolejną próbę i rejestruje ją w audycie. */
    private PartialGrade issueOne(GroupEnrollment enrollment, AssessmentItem item, GradeValue value, User issuer) {
        Short lastAttempt = grades.findMaxAttempt(enrollment.getId(), item.getId());
        short attempt = (short) (lastAttempt == null ? 1 : lastAttempt + 1);

        PartialGrade grade = grades.save(new PartialGrade(enrollment, item, value, attempt, issuer));
        audit.record(AUDIT_ENTITY, grade.getId(), AuditAction.CREATE, null, snapshot(grade));
        return grade;
    }

    /** Ocena musi pochodzić ze skali elementu zaliczenia: na element „na ocenę” nie da się wpisać „zal”. */
    private GradeValue gradeValueForItem(UUID gradeValueId, AssessmentItem item) {
        GradeValue value = gradeValues.findById(gradeValueId)
                .orElseThrow(() -> new NotFoundException("Grade value not found"));
        if (!value.getGradingScale().getId().equals(item.getGradingScale().getId())) {
            throw new BusinessRuleException("Grade value does not belong to the grading scale of this assessment item");
        }
        return value;
    }

    private ClassGroup myGroup(UUID groupId) {
        return groups.findDetailed(groupId)
                .filter(this::isMyGroup)
                .orElseThrow(GradeService::groupNotFound);
    }

    private boolean isMyGroup(ClassGroup group) {
        return group.getLecturer().getUserId().equals(CurrentUser.id());
    }

    private boolean canViewGroup(ClassGroup group) {
        return switch (CurrentUser.role()) {
            case ADMIN -> true;
            case LECTURER -> isMyGroup(group);
            case DEAN_OFFICE -> group.facultyId().equals(deanOffice.facultyId());
            case STUDENT -> enrollments.existsByClassGroupIdAndStudyStudentUserId(group.getId(), CurrentUser.id());
        };
    }

    private static Map<String, Object> snapshot(PartialGrade grade) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("enrollmentId", grade.getGroupEnrollment().getId().toString());
        snapshot.put("studentId", grade.getGroupEnrollment().getStudy().getStudent().getUserId().toString());
        snapshot.put("assessmentItemId", grade.getAssessmentItem().getId().toString());
        snapshot.put("grade", grade.getGradeValue().getLabel());
        snapshot.put("attempt", grade.getAttempt());
        snapshot.put("issuedBy", grade.getIssuedBy().getId().toString());
        return snapshot;
    }

    private static NotFoundException gradeNotFound() {
        return new NotFoundException("Partial grade not found");
    }

    private static NotFoundException groupNotFound() {
        return new NotFoundException("Group not found");
    }
}
