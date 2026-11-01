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
import pl.tokstudiow.api.people.CurrentDeanOffice;
import pl.tokstudiow.api.university.GradeValue;
import pl.tokstudiow.api.university.GradeValueRepository;
import pl.tokstudiow.api.user.UserRepository;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class GradeService {

    private static final BigDecimal MAX_TOTAL_WEIGHT = new BigDecimal("100");

    private final ClassGroupRepository groups;
    private final GroupEnrollmentRepository enrollments;
    private final AssessmentItemRepository items;
    private final PartialGradeRepository grades;
    private final GradeValueRepository gradeValues;
    private final UserRepository users;
    private final CurrentDeanOffice deanOffice;
    private final AuditService audit;

    public GradeService(ClassGroupRepository groups,
                        GroupEnrollmentRepository enrollments,
                        AssessmentItemRepository items,
                        PartialGradeRepository grades,
                        GradeValueRepository gradeValues,
                        UserRepository users,
                        CurrentDeanOffice deanOffice,
                        AuditService audit) {
        this.groups = groups;
        this.enrollments = enrollments;
        this.items = items;
        this.grades = grades;
        this.gradeValues = gradeValues;
        this.users = users;
        this.deanOffice = deanOffice;
        this.audit = audit;
    }

    // elementy zaliczenia

    @Transactional
    public AssessmentItemResponse addItem(UUID groupId, AssessmentItemRequest request) {
        // Blokada grupy: dwa równoczesne dodania nie przekroczą razem 100%
        ClassGroup group = groups.findByIdForUpdate(groupId)
                .filter(this::isMyGroup)
                .orElseThrow(GradeService::groupNotFound);

        BigDecimal current = items.sumWeights(groupId);
        BigDecimal total = (current == null ? BigDecimal.ZERO : current).add(request.weightPercent());
        if (total.compareTo(MAX_TOTAL_WEIGHT) > 0) {
            throw new BusinessRuleException("Total weight of assessment items cannot exceed 100%");
        }

        short orderIndex = (short) (items.countByClassGroupId(groupId) + 1);
        AssessmentItem item = items.save(new AssessmentItem(
                group, request.name().trim(), request.weightPercent(), orderIndex, request.plannedDate()));

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("classGroupId", groupId.toString());
        snapshot.put("name", item.getName());
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

    // oceny cząstkowe

    @Transactional
    public PartialGradeResponse issue(UUID groupId, PartialGradeRequest request) {
        ClassGroup group = groups.findDetailed(groupId)
                .filter(this::isMyGroup)
                .orElseThrow(GradeService::groupNotFound);

        GroupEnrollment enrollment = enrollments.findById(request.enrollmentId())
                .filter(e -> e.getClassGroup().getId().equals(groupId))
                .orElseThrow(() -> new NotFoundException("Enrollment not found"));
        AssessmentItem item = items.findById(request.assessmentItemId())
                .filter(i -> i.getClassGroup().getId().equals(groupId))
                .orElseThrow(() -> new NotFoundException("Assessment item not found"));
        GradeValue value = gradeValues.findById(request.gradeValueId())
                .orElseThrow(() -> new NotFoundException("Grade value not found"));

        UUID requiredScale = group.getCourseComponent().getGradingScale().getId();
        if (!value.getGradingScale().getId().equals(requiredScale)) {
            throw new BusinessRuleException("Grade value does not belong to the grading scale of this course component");
        }

        Short lastAttempt = grades.findMaxAttempt(enrollment.getId(), item.getId());
        short attempt = (short) (lastAttempt == null ? 1 : lastAttempt + 1);

        PartialGrade grade = grades.save(new PartialGrade(
                enrollment, item, value, attempt, users.getReferenceById(CurrentUser.id())));

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("enrollmentId", enrollment.getId().toString());
        snapshot.put("studentId", enrollment.getStudy().getStudent().getUserId().toString());
        snapshot.put("assessmentItemId", item.getId().toString());
        snapshot.put("grade", value.getLabel());
        snapshot.put("attempt", attempt);
        snapshot.put("issuedBy", CurrentUser.id().toString());
        audit.record("partial_grade", grade.getId(), AuditAction.CREATE, null, snapshot);

        return PartialGradeResponse.from(grade);
    }

    @Transactional(readOnly = true)
    public List<PartialGradeResponse> gradebook(UUID groupId) {
        groups.findDetailed(groupId)
                .filter(this::isMyGroup)
                .orElseThrow(GradeService::groupNotFound);
        return grades.findForGroup(groupId).stream().map(PartialGradeResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<PartialGradeResponse> myGrades(UUID groupId) {
        UUID studentId = CurrentUser.id();
        if (!enrollments.existsByClassGroupIdAndStudyStudentUserId(groupId, studentId)) {
            throw groupNotFound();
        }
        return grades.findForStudentInGroup(groupId, studentId).stream().map(PartialGradeResponse::from).toList();
    }

    // kontrola dostępu

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

    private static NotFoundException groupNotFound() {
        return new NotFoundException("Group not found");
    }
}