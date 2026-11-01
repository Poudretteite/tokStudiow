package pl.tokstudiow.api.grades;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tokstudiow.api.audit.AuditAction;
import pl.tokstudiow.api.audit.AuditService;
import pl.tokstudiow.api.common.BusinessRuleException;
import pl.tokstudiow.api.common.CurrentUser;
import pl.tokstudiow.api.common.NotFoundException;
import pl.tokstudiow.api.groups.ClassGroup;
import pl.tokstudiow.api.groups.ClassGroupRepository;
import pl.tokstudiow.api.groups.GroupEnrollment;
import pl.tokstudiow.api.groups.GroupEnrollmentRepository;
import pl.tokstudiow.api.studies.StudyRepository;
import pl.tokstudiow.api.university.GradeValue;
import pl.tokstudiow.api.university.GradeValueRepository;
import pl.tokstudiow.api.university.GradingScaleType;
import pl.tokstudiow.api.user.UserRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class FinalGradeService {

    private static final String AUDIT_ENTITY = "final_grade";
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final ClassGroupRepository groups;
    private final GroupEnrollmentRepository enrollments;
    private final FinalGradeRepository finalGrades;
    private final AssessmentItemRepository items;
    private final PartialGradeRepository partialGrades;
    private final GradeValueRepository gradeValues;
    private final StudyRepository studies;
    private final UserRepository users;
    private final AuditService audit;

    public FinalGradeService(ClassGroupRepository groups,
                             GroupEnrollmentRepository enrollments,
                             FinalGradeRepository finalGrades,
                             AssessmentItemRepository items,
                             PartialGradeRepository partialGrades,
                             GradeValueRepository gradeValues,
                             StudyRepository studies,
                             UserRepository users,
                             AuditService audit) {
        this.groups = groups;
        this.enrollments = enrollments;
        this.finalGrades = finalGrades;
        this.items = items;
        this.partialGrades = partialGrades;
        this.gradeValues = gradeValues;
        this.studies = studies;
        this.users = users;
        this.audit = audit;
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

    // prowadzący: propozycja oceny

    @Transactional(readOnly = true)
    public List<FinalGradeProposalResponse> proposals(UUID groupId) {
        ClassGroup group = myGroup(groupId);
        var scale = group.getCourseComponent().getGradingScale();
        List<AssessmentItem> groupItems = items.findByClassGroupIdOrderByOrderIndex(groupId);
        BigDecimal totalWeight = groupItems.stream()
                .map(AssessmentItem::getWeightPercent)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean computable = scale.getType() == GradingScaleType.NUMERIC
                && !groupItems.isEmpty()
                && totalWeight.compareTo(HUNDRED) == 0;

        // ostatnia próba z każdego elementu: klucz = zapis + element
        Map<String, PartialGrade> latest = new HashMap<>();
        for (PartialGrade grade : partialGrades.findForGroup(groupId)) {
            latest.put(grade.getGroupEnrollment().getId() + ":" + grade.getAssessmentItem().getId(), grade);
        }
        List<GradeValue> scaleValues = scale.getValues().stream()
                .filter(v -> v.getNumericValue() != null)
                .sorted(Comparator.comparing(GradeValue::getNumericValue))
                .toList();

        return enrollments.findRoster(groupId).stream().map(enrollment -> {
            BigDecimal average = computable ? weightedAverage(enrollment, groupItems, latest) : null;
            GradeValue proposed = average == null ? null : nearest(average, scaleValues);
            var student = enrollment.getStudy().getStudent();
            return new FinalGradeProposalResponse(
                    enrollment.getId(),
                    student.getUserId(),
                    student.getUser().getFirstName(),
                    student.getUser().getLastName(),
                    student.getAlbumNumber(),
                    average,
                    proposed == null ? null : proposed.getId(),
                    proposed == null ? null : proposed.getLabel());
        }).toList();
    }

    /** Średnia ważona; null, jeśli student nie ma oceny z któregoś elementu. */
    private static BigDecimal weightedAverage(GroupEnrollment enrollment, List<AssessmentItem> groupItems,
                                              Map<String, PartialGrade> latest) {
        BigDecimal sum = BigDecimal.ZERO;
        for (AssessmentItem item : groupItems) {
            PartialGrade grade = latest.get(enrollment.getId() + ":" + item.getId());
            if (grade == null || grade.getGradeValue().getNumericValue() == null) {
                return null;
            }
            sum = sum.add(item.getWeightPercent().multiply(grade.getGradeValue().getNumericValue()));
        }
        return sum.divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }

    /** Najbliższa ocena ze skali; przy remisie wyższa. */
    private static GradeValue nearest(BigDecimal average, List<GradeValue> ascendingValues) {
        GradeValue best = ascendingValues.getFirst();
        for (GradeValue candidate : ascendingValues) {
            BigDecimal distance = candidate.getNumericValue().subtract(average).abs();
            BigDecimal bestDistance = best.getNumericValue().subtract(average).abs();
            if (distance.compareTo(bestDistance) <= 0) {
                best = candidate;
            }
        }
        return best;
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