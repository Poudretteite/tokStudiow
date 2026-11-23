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
import pl.tokstudiow.api.grades.GradeProposalCalculator.ItemResult;
import pl.tokstudiow.api.grades.GradeProposalCalculator.Proposal;
import pl.tokstudiow.api.grades.GradeProposalCalculator.ScaleValue;
import pl.tokstudiow.api.groups.ClassGroup;
import pl.tokstudiow.api.groups.ClassGroupRepository;
import pl.tokstudiow.api.groups.GroupEnrollment;
import pl.tokstudiow.api.groups.GroupEnrollmentRepository;
import pl.tokstudiow.api.people.CurrentDeanOffice;
import pl.tokstudiow.api.studies.StudyRepository;
import pl.tokstudiow.api.university.GradeValue;
import pl.tokstudiow.api.university.GradeValueRepository;
import pl.tokstudiow.api.university.GradingScale;
import pl.tokstudiow.api.university.GradingScaleType;
import pl.tokstudiow.api.user.User;
import pl.tokstudiow.api.user.UserRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class FinalGradeService {

    private static final String AUDIT_ENTITY = "final_grade";

    private final ClassGroupRepository groups;
    private final GroupEnrollmentRepository enrollments;
    private final FinalGradeRepository finalGrades;
    private final AssessmentItemRepository items;
    private final PartialGradeRepository partialGrades;
    private final GradeValueRepository gradeValues;
    private final StudyRepository studies;
    private final UserRepository users;
    private final CurrentDeanOffice deanOffice;
    private final AuditService audit;
    private final Clock clock;

    public FinalGradeService(ClassGroupRepository groups,
                             GroupEnrollmentRepository enrollments,
                             FinalGradeRepository finalGrades,
                             AssessmentItemRepository items,
                             PartialGradeRepository partialGrades,
                             GradeValueRepository gradeValues,
                             StudyRepository studies,
                             UserRepository users,
                             CurrentDeanOffice deanOffice,
                             AuditService audit,
                             Clock clock) {
        this.groups = groups;
        this.enrollments = enrollments;
        this.finalGrades = finalGrades;
        this.items = items;
        this.partialGrades = partialGrades;
        this.gradeValues = gradeValues;
        this.studies = studies;
        this.users = users;
        this.deanOffice = deanOffice;
        this.audit = audit;
        this.clock = clock;
    }

    // prowadzący: szkic

    @Transactional
    public FinalGradeResponse saveDraft(UUID groupId, FinalGradeRequest request) {
        ClassGroup group = myGroup(groupId);
        GroupEnrollment enrollment = enrollments.findById(request.enrollmentId())
                .filter(e -> e.getClassGroup().getId().equals(groupId))
                .orElseThrow(() -> new NotFoundException("Enrollment not found"));
        GradeValue value = gradeValues.findById(request.gradeValueId())
                .orElseThrow(() -> new NotFoundException("Grade value not found"));
        if (!value.getGradingScale().getId().equals(group.getCourseComponent().getGradingScale().getId())) {
            throw new BusinessRuleException("Grade value does not belong to the grading scale of this course component");
        }

        Optional<FinalGrade> existing = finalGrades.findByGroupEnrollmentIdAndAttempt(enrollment.getId(), request.attempt());
        if (existing.isPresent()) {
            FinalGrade grade = existing.get();
            Map<String, Object> before = snapshot(grade);
            grade.changeValue(value); // rzuca 409, jeśli ocena jest już zatwierdzona
            audit.record(AUDIT_ENTITY, grade.getId(), AuditAction.UPDATE, before, snapshot(grade));
            return FinalGradeResponse.from(grade);
        }

        requirePreviousAttemptFailed(enrollment, request.attempt());
        FinalGrade grade = finalGrades.save(new FinalGrade(enrollment, request.attempt(), value));
        audit.record(AUDIT_ENTITY, grade.getId(), AuditAction.CREATE, null, snapshot(grade));
        return FinalGradeResponse.from(grade);
    }

    // prowadzący: zatwierdzenie

    @Transactional
    public FinalGradeResponse approve(UUID groupId, UUID finalGradeId) {
        myGroup(groupId);
        FinalGrade grade = finalGrades.findById(finalGradeId)
                .filter(g -> g.getGroupEnrollment().getClassGroup().getId().equals(groupId))
                .orElseThrow(() -> new NotFoundException("Final grade not found"));

        Map<String, Object> before = snapshot(grade);
        grade.approve(users.getReferenceById(CurrentUser.id()));
        audit.record(AUDIT_ENTITY, grade.getId(), AuditAction.UPDATE, before, snapshot(grade));
        return FinalGradeResponse.from(grade);
    }

    @Transactional(readOnly = true)
    public List<FinalGradeResponse> listForGroup(UUID groupId) {
        myGroup(groupId);
        return finalGrades.findForGroup(groupId).stream().map(FinalGradeResponse::from).toList();
    }

    // prowadzący: arkusz ocen końcowych

    /**
     * Arkusz całej grupy: propozycja, dotychczasowe terminy i termin, którego dotyczy
     * zatwierdzenie. Frontend wpisuje propozycję jako ocenę, prowadzący zmienia tylko wyjątki.
     */
    @Transactional(readOnly = true)
    public List<FinalGradeSheetRowResponse> sheet(UUID groupId) {
        ClassGroup group = myGroup(groupId);
        List<GroupEnrollment> roster = enrollments.findRoster(groupId);
        Map<UUID, Proposal> proposalsByEnrollment = proposalsFor(group, roster);
        Map<UUID, List<FinalGrade>> gradesByEnrollment = gradesByEnrollment(groupId);

        return roster.stream().map(enrollment -> {
            Proposal proposal = proposalsByEnrollment.get(enrollment.getId());
            List<FinalGrade> attempts = gradesByEnrollment.getOrDefault(enrollment.getId(), List.of());
            var student = enrollment.getStudy().getStudent();
            return new FinalGradeSheetRowResponse(
                    enrollment.getId(),
                    student.getUserId(),
                    student.getUser().getFirstName(),
                    student.getUser().getLastName(),
                    student.getAlbumNumber(),
                    proposal.average(),
                    proposal.value() == null ? null : proposal.value().id(),
                    proposal.value() == null ? null : proposal.value().label(),
                    attempts.stream().map(FinalGradeResponse::from).toList(),
                    openAttempt(attempts),
                    attempts.stream()
                            .filter(g -> !g.isApproved())
                            .findFirst()
                            .map(g -> g.getGradeValue().getId())
                            .orElse(null));
        }).toList();
    }

    /**
     * Zatwierdza oceny końcowe wielu studentów jednym żądaniem, atomowo: błąd w jednym
     * wpisie wycofuje całość. Termin wybiera system (I termin, poprawka po niezaliczonym,
     * komisja), więc prowadzący nie może np. wpisać poprawki osobie, która zdała.
     */
    @Transactional
    public List<FinalGradeResponse> approveBatch(UUID groupId, FinalGradeBatchRequest request) {
        // Blokada grupy: dwa równoczesne zatwierdzenia nie utworzą dwóch ocen za ten sam termin
        ClassGroup group = groups.findByIdForUpdate(groupId)
                .filter(g -> g.getLecturer().getUserId().equals(CurrentUser.id()))
                .orElseThrow(() -> new NotFoundException("Group not found"));

        Set<UUID> seen = new HashSet<>();
        for (BatchGradeEntry entry : request.entries()) {
            if (!seen.add(entry.enrollmentId())) {
                throw new BusinessRuleException("Each student may appear only once in the request");
            }
        }

        Map<UUID, GroupEnrollment> roster = enrollments.findRoster(groupId).stream()
                .collect(Collectors.toMap(GroupEnrollment::getId, Function.identity()));
        Map<UUID, GradeValue> scaleValues = group.getCourseComponent().getGradingScale().getValues().stream()
                .collect(Collectors.toMap(GradeValue::getId, Function.identity()));
        Map<UUID, List<FinalGrade>> gradesByEnrollment = gradesByEnrollment(groupId);
        User approver = users.getReferenceById(CurrentUser.id());

        List<FinalGradeResponse> approved = new ArrayList<>();
        for (BatchGradeEntry entry : request.entries()) {
            GroupEnrollment enrollment = roster.get(entry.enrollmentId());
            if (enrollment == null) {
                throw new NotFoundException("Enrollment not found");
            }
            GradeValue value = scaleValues.get(entry.gradeValueId());
            if (value == null) {
                throw new BusinessRuleException("Grade value does not belong to the grading scale of this course component");
            }
            List<FinalGrade> attempts = gradesByEnrollment.getOrDefault(enrollment.getId(), List.of());
            FinalAttempt attempt = openAttempt(attempts);
            if (attempt == null) {
                throw new ConflictException("Final grade is already settled");
            }

            Optional<FinalGrade> draft = attempts.stream().filter(g -> g.getAttempt() == attempt).findFirst();
            FinalGrade grade;
            if (draft.isPresent()) {
                grade = draft.get();
                Map<String, Object> before = snapshot(grade);
                grade.changeValue(value);
                grade.approve(approver);
                audit.record(AUDIT_ENTITY, grade.getId(), AuditAction.UPDATE, before, snapshot(grade));
            } else {
                // od razu jako zatwierdzona: jeden INSERT i jeden wpis w audycie
                FinalGrade fresh = new FinalGrade(enrollment, attempt, value);
                fresh.approve(approver);
                grade = finalGrades.save(fresh);
                audit.record(AUDIT_ENTITY, grade.getId(), AuditAction.CREATE, null, snapshot(grade));
            }
            approved.add(FinalGradeResponse.from(grade));
        }
        return approved;
    }

    /**
     * Termin, który można teraz zatwierdzić: pierwszy, otwarty szkic albo kolejny
     * po zatwierdzonym niezaliczonym. Null, gdy student zaliczył albo wyczerpał terminy.
     */
    private static @Nullable FinalAttempt openAttempt(List<FinalGrade> attemptsAscending) {
        if (attemptsAscending.isEmpty()) {
            return FinalAttempt.FIRST;
        }
        FinalGrade last = attemptsAscending.getLast();
        if (!last.isApproved()) {
            return last.getAttempt();
        }
        return last.getGradeValue().isPassing() ? null : last.getAttempt().next();
    }

    private Map<UUID, List<FinalGrade>> gradesByEnrollment(UUID groupId) {
        return finalGrades.findForGroup(groupId).stream()
                .sorted(Comparator.comparing(FinalGrade::getAttempt))
                .collect(Collectors.groupingBy(g -> g.getGroupEnrollment().getId()));
    }

    // prowadzący: propozycja oceny

    /**
     * Propozycja oceny końcowej na podstawie ostatnich prób z każdego elementu zaliczenia.
     * Elementy „na ocenę” tworzą średnią ważoną (wagi muszą dawać 100%),
     * elementy zal/nzal są warunkiem zaliczenia: niezaliczony daje propozycję oceny niedostatecznej.
     * Brak oceny z któregokolwiek elementu = brak propozycji.
     */
    @Transactional(readOnly = true)
    public List<FinalGradeProposalResponse> proposals(UUID groupId) {
        ClassGroup group = myGroup(groupId);
        List<GroupEnrollment> roster = enrollments.findRoster(groupId);
        Map<UUID, Proposal> proposalsByEnrollment = proposalsFor(group, roster);

        return roster.stream().map(enrollment -> {
            Proposal proposal = proposalsByEnrollment.get(enrollment.getId());
            var student = enrollment.getStudy().getStudent();
            return new FinalGradeProposalResponse(
                    enrollment.getId(),
                    student.getUserId(),
                    student.getUser().getFirstName(),
                    student.getUser().getLastName(),
                    student.getAlbumNumber(),
                    proposal.average(),
                    proposal.value() == null ? null : proposal.value().id(),
                    proposal.value() == null ? null : proposal.value().label());
        }).toList();
    }

    /** Propozycje dla wszystkich studentów z listy, według identyfikatora zapisu do grupy. */
    private Map<UUID, Proposal> proposalsFor(ClassGroup group, List<GroupEnrollment> roster) {
        GradingScale componentScale = group.getCourseComponent().getGradingScale();
        boolean passFail = componentScale.getType() == GradingScaleType.PASS_FAIL;
        List<ScaleValue> scaleValues = componentScale.getValues().stream().map(ScaleValue::of).toList();
        List<AssessmentItem> allItems = items.findByClassGroupIdOrderByOrderIndex(group.getId());

        // ostatnia próba z każdego elementu: lista jest posortowana po próbie, późniejsza nadpisuje
        Map<String, PartialGrade> latest = new HashMap<>();
        for (PartialGrade grade : partialGrades.findForGroup(group.getId())) {
            latest.put(key(grade.getGroupEnrollment(), grade.getAssessmentItem()), grade);
        }

        Map<UUID, Proposal> result = new HashMap<>();
        for (GroupEnrollment enrollment : roster) {
            List<ItemResult> results = allItems.stream().map(item -> {
                PartialGrade last = latest.get(key(enrollment, item));
                return new ItemResult(
                        item.getGradingScale().getType() == GradingScaleType.NUMERIC,
                        item.getWeightPercent(),
                        last == null ? null : ScaleValue.of(last.getGradeValue()));
            }).toList();
            result.put(enrollment.getId(), GradeProposalCalculator.propose(passFail, scaleValues, results));
        }
        return result;
    }

    private static String key(GroupEnrollment enrollment, AssessmentItem item) {
        return enrollment.getId() + ":" + item.getId();
    }

    // dziekanat: protokół i korekta zatwierdzonej oceny

    /** Oceny końcowe grupy z wydziału pracownika dziekanatu (protokół). */
    @Transactional(readOnly = true)
    public List<FinalGradeResponse> protocol(UUID groupId) {
        groups.findDetailed(groupId)
                .filter(g -> g.facultyId().equals(deanOffice.facultyId()))
                .orElseThrow(() -> new NotFoundException("Group not found"));
        return finalGrades.findForGroup(groupId).stream()
                .sorted(Comparator.comparing((FinalGrade g) -> g.getGroupEnrollment().getStudy().getStudent().getUser().getLastName())
                        .thenComparing(g -> g.getGroupEnrollment().getStudy().getStudent().getUser().getFirstName())
                        .thenComparing(FinalGrade::getAttempt))
                .map(FinalGradeResponse::from)
                .toList();
    }

    /**
     * Korekta zatwierdzonej oceny końcowej przez dziekanat – także po terminie korekt.
     * Zawsze z komentarzem (podstawą zmiany), który trafia do audytu.
     */
    @Transactional
    public FinalGradeResponse correctApproved(UUID finalGradeId, CorrectGradeRequest request) {
        UUID facultyId = deanOffice.facultyId();
        FinalGrade grade = finalGrades.findById(finalGradeId)
                .filter(g -> g.getGroupEnrollment().getClassGroup().facultyId().equals(facultyId))
                .orElseThrow(FinalGradeService::finalGradeNotFound);
        return applyCorrection(grade, request.gradeValueId(), request.reason());
    }

    /**
     * Korekta pomyłki w zatwierdzonej ocenie przez prowadzącego, bez podawania powodu.
     * I termin – do końca sesji + 2 dni, poprawka i komisja – do końca sesji poprawkowej + 2 dni.
     * Po terminie ocenę zmienia już tylko dziekanat.
     */
    @Transactional
    public FinalGradeResponse correctAsLecturer(UUID groupId, UUID finalGradeId, LecturerCorrectionRequest request) {
        ClassGroup group = myGroup(groupId);
        FinalGrade grade = finalGrades.findById(finalGradeId)
                .filter(g -> g.getGroupEnrollment().getClassGroup().getId().equals(groupId))
                .orElseThrow(FinalGradeService::finalGradeNotFound);
        // I termin: do końca sesji + 2 dni; poprawka i komisja: do końca sesji poprawkowej + 2 dni
        boolean retake = grade.getAttempt() != FinalAttempt.FIRST;
        if (!group.getAcademicTerm().allowsLecturerCorrection(LocalDate.now(clock), retake)) {
            throw new ConflictException("Correction period has ended");
        }
        return applyCorrection(grade, request.gradeValueId(), null);
    }

    /**
     * Wspólne reguły korekty: tylko zatwierdzona ocena (szkic zmienia się w arkuszu) i tylko ostatni
     * termin studenta – zmiana wcześniejszego unieważniłaby sens kolejnych. Stara i nowa wartość
     * oraz osoba korygująca trafiają do audytu; komentarz dziekanatu również.
     */
    private FinalGradeResponse applyCorrection(FinalGrade grade, UUID gradeValueId, @Nullable String reason) {
        boolean laterAttemptExists = finalGrades.findForGroup(grade.getGroupEnrollment().getClassGroup().getId()).stream()
                .anyMatch(g -> g.getGroupEnrollment().getId().equals(grade.getGroupEnrollment().getId())
                        && g.getAttempt().compareTo(grade.getAttempt()) > 0);
        if (laterAttemptExists) {
            throw new ConflictException("Only the latest attempt can be corrected");
        }

        GradeValue value = gradeValues.findById(gradeValueId)
                .orElseThrow(() -> new NotFoundException("Grade value not found"));
        GradingScale scale = grade.getGroupEnrollment().getClassGroup().getCourseComponent().getGradingScale();
        if (!value.getGradingScale().getId().equals(scale.getId())) {
            throw new BusinessRuleException("Grade value does not belong to the grading scale of this course component");
        }
        if (!grade.isApproved()) {
            throw new ConflictException("Only approved final grades can be corrected");
        }
        if (value.getId().equals(grade.getGradeValue().getId())) {
            return FinalGradeResponse.from(grade);
        }

        Map<String, Object> before = snapshot(grade);
        grade.correctApproved(value);
        Map<String, Object> after = snapshot(grade);
        after.put("correctedBy", CurrentUser.id().toString());
        if (reason != null && !reason.isBlank()) {
            after.put("reason", reason.trim());
        }
        audit.record(AUDIT_ENTITY, grade.getId(), AuditAction.UPDATE, before, after);
        return FinalGradeResponse.from(grade);
    }

    private static NotFoundException finalGradeNotFound() {
        return new NotFoundException("Final grade not found");
    }

    // student: indeks

    @Transactional(readOnly = true)
    public List<StudyTranscriptResponse> myTranscript() {
        UUID studentId = CurrentUser.id();
        Map<UUID, List<FinalGrade>> byStudy = finalGrades.findForStudent(studentId, FinalGradeStatus.APPROVED).stream()
                .collect(Collectors.groupingBy(f -> f.getGroupEnrollment().getStudy().getId()));

        return studies.findForStudent(studentId).stream().map(study -> {
            List<TranscriptEntryResponse> entries = byStudy.getOrDefault(study.getId(), List.of()).stream()
                    .sorted(Comparator
                            .comparing((FinalGrade f) -> f.getGroupEnrollment().getClassGroup().getAcademicTerm().getStartDate())
                            .thenComparing(f -> f.getGroupEnrollment().getClassGroup().getCourseComponent().getCourse().getName())
                            .thenComparing(FinalGrade::getAttempt))
                    .map(FinalGradeService::toTranscriptEntry)
                    .toList();
            Double average = finalGrades.averageForStudy(study.getId(), FinalGradeStatus.APPROVED);
            return new StudyTranscriptResponse(
                    study.getId(),
                    study.getFieldOfStudy().getName(),
                    average == null ? null : BigDecimal.valueOf(average).setScale(2, RoundingMode.HALF_UP),
                    entries);
        }).toList();
    }

    private static TranscriptEntryResponse toTranscriptEntry(FinalGrade grade) {
        var group = grade.getGroupEnrollment().getClassGroup();
        var component = group.getCourseComponent();
        var course = component.getCourse();
        var value = grade.getGradeValue();
        return new TranscriptEntryResponse(
                course.getCode(), course.getName(), component.getType(), course.getEcts(),
                group.getAcademicTerm().getName(), grade.getAttempt(),
                value.getLabel(), value.getNumericValue(), value.isPassing(), grade.getApprovedAt());
    }

    // pomocnicze

    private ClassGroup myGroup(UUID groupId) {
        return groups.findDetailed(groupId)
                .filter(g -> g.getLecturer().getUserId().equals(CurrentUser.id()))
                .orElseThrow(() -> new NotFoundException("Group not found"));
    }

    private void requirePreviousAttemptFailed(GroupEnrollment enrollment, FinalAttempt attempt) {
        FinalAttempt previous = attempt.previous();
        if (previous == null) {
            return;
        }
        boolean previousFailed = finalGrades.findByGroupEnrollmentIdAndAttempt(enrollment.getId(), previous)
                .filter(FinalGrade::isApproved)
                .map(g -> !g.getGradeValue().isPassing())
                .orElse(false);
        if (!previousFailed) {
            throw new BusinessRuleException("Previous attempt must be approved and failed");
        }
    }

    private static Map<String, Object> snapshot(FinalGrade grade) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("enrollmentId", grade.getGroupEnrollment().getId().toString());
        snapshot.put("studentId", grade.getGroupEnrollment().getStudy().getStudent().getUserId().toString());
        snapshot.put("attempt", grade.getAttempt().name());
        snapshot.put("grade", grade.getGradeValue().getLabel());
        snapshot.put("status", grade.getStatus().name());
        snapshot.put("approvedBy", grade.getApprovedBy() == null ? null : grade.getApprovedBy().getId().toString());
        return snapshot;
    }
}
