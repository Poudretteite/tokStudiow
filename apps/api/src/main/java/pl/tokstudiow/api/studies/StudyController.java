package pl.tokstudiow.api.studies;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
public class StudyController {

    private final StudyService studyService;
    private final StudentImportService importService;

    public StudyController(StudyService studyService, StudentImportService importService) {
        this.studyService = studyService;
        this.importService = importService;
    }

    @GetMapping("/api/me/studies")
    @PreAuthorize("hasRole('STUDENT')")
    public List<StudyResponse> myStudies() {
        return studyService.myStudies();
    }

    @GetMapping("/api/studies/{id}")
    public StudyResponse get(@PathVariable UUID id) {
        return studyService.get(id);
    }

    @GetMapping("/api/dean-office/studies")
    @PreAuthorize("hasRole('DEAN_OFFICE')")
    public List<StudyResponse> listForMyFaculty() {
        return studyService.listForMyFaculty();
    }

    @GetMapping("/api/dean-office/students/unassigned")
    @PreAuthorize("hasRole('DEAN_OFFICE')")
    public List<UnassignedStudentResponse> unassignedStudents() {
        return studyService.unassignedStudents();
    }

    @PostMapping("/api/dean-office/students/import")
    @PreAuthorize("hasRole('DEAN_OFFICE')")
    @ResponseStatus(HttpStatus.CREATED)
    public List<ImportedStudentResponse> importStudents(@Valid @RequestBody StudentImportRequest request) {
        return importService.importStudents(request);
    }

    @PostMapping("/api/dean-office/studies/batch-update")
    @PreAuthorize("hasRole('DEAN_OFFICE')")
    public List<StudyResponse> batchUpdate(@Valid @RequestBody StudyBatchUpdateRequest request) {
        return studyService.batchUpdate(request);
    }

    @PostMapping("/api/dean-office/studies")
    @PreAuthorize("hasRole('DEAN_OFFICE')")
    @ResponseStatus(HttpStatus.CREATED)
    public StudyResponse create(@Valid @RequestBody CreateStudyRequest request) {
        return studyService.create(request);
    }

    @PutMapping("/api/dean-office/studies/{id}")
    @PreAuthorize("hasRole('DEAN_OFFICE')")
    public StudyResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateStudyRequest request) {
        return studyService.update(id, request);
    }
}
