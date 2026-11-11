package pl.tokstudiow.api.grades;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
public class GradeController {

    private final GradeService gradeService;

    public GradeController(GradeService gradeService) {
        this.gradeService = gradeService;
    }

    // elementy zaliczenia

    @PostMapping("/api/lecturer/groups/{groupId}/assessment-items")
    @PreAuthorize("hasRole('LECTURER')")
    @ResponseStatus(HttpStatus.CREATED)
    public AssessmentItemResponse addItem(@PathVariable UUID groupId,
                                          @Valid @RequestBody AssessmentItemRequest request) {
        return gradeService.addItem(groupId, request);
    }

    @GetMapping("/api/groups/{groupId}/assessment-items")
    public List<AssessmentItemResponse> listItems(@PathVariable UUID groupId) {
        return gradeService.listItems(groupId);
    }

    // oceny cząstkowe: prowadzący

    @PostMapping("/api/lecturer/groups/{groupId}/partial-grades")
    @PreAuthorize("hasRole('LECTURER')")
    @ResponseStatus(HttpStatus.CREATED)
    public PartialGradeResponse issue(@PathVariable UUID groupId,
                                      @Valid @RequestBody PartialGradeRequest request) {
        return gradeService.issue(groupId, request);
    }

    @PostMapping("/api/lecturer/groups/{groupId}/partial-grades/batch")
    @PreAuthorize("hasRole('LECTURER')")
    @ResponseStatus(HttpStatus.CREATED)
    public List<PartialGradeResponse> issueBatch(@PathVariable UUID groupId,
                                                 @Valid @RequestBody PartialGradeBatchRequest request) {
        return gradeService.issueBatch(groupId, request);
    }

    @PatchMapping("/api/lecturer/groups/{groupId}/partial-grades/{gradeId}")
    @PreAuthorize("hasRole('LECTURER')")
    public PartialGradeResponse correct(@PathVariable UUID groupId,
                                        @PathVariable UUID gradeId,
                                        @Valid @RequestBody CorrectGradeRequest request) {
        return gradeService.correct(groupId, gradeId, request);
    }

    @GetMapping("/api/lecturer/groups/{groupId}/partial-grades")
    @PreAuthorize("hasRole('LECTURER')")
    public List<PartialGradeResponse> gradebook(@PathVariable UUID groupId) {
        return gradeService.gradebook(groupId);
    }

    // oceny cząstkowe: student

    @GetMapping("/api/me/groups/{groupId}/partial-grades")
    @PreAuthorize("hasRole('STUDENT')")
    public List<PartialGradeResponse> myGrades(@PathVariable UUID groupId) {
        return gradeService.myGrades(groupId);
    }
}