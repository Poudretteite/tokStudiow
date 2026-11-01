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

    @PostMapping("/api/lecturer/groups/{groupId}/assessment-items")
    @PreAuthorize("hasRole('LECTURER')")
    @ResponseStatus(HttpStatus.CREATED)
    public AssessmentItemResponse addItem(@PathVariable UUID groupId, @Valid @RequestBody AssessmentItemRequest request) {
        return gradeService.addItem(groupId, request);
    }

    @GetMapping("/api/groups/{groupId}/assessment-items")
    public List<AssessmentItemResponse> listItems(@PathVariable UUID groupId) {
        return gradeService.listItems(groupId);
    }

    @PostMapping("/api/lecturer/groups/{groupId}/partial-grades")
    @PreAuthorize("hasRole('LECTURER')")
    @ResponseStatus(HttpStatus.CREATED)
    public PartialGradeResponse issue(@PathVariable UUID groupId, @Valid @RequestBody PartialGradeRequest request) {
        return gradeService.issue(groupId, request);
    }

    @GetMapping("/api/lecturer/groups/{groupId}/partial-grades")
    @PreAuthorize("hasRole('LECTURER')")
    public List<PartialGradeResponse> gradebook(@PathVariable UUID groupId) {
        return gradeService.gradebook(groupId);
    }

    @GetMapping("/api/me/groups/{groupId}/partial-grades")
    @PreAuthorize("hasRole('STUDENT')")
    public List<PartialGradeResponse> myGrades(@PathVariable UUID groupId) {
        return gradeService.myGrades(groupId);
    }
}