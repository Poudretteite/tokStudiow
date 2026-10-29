package pl.tokstudiow.api.university;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
public class FieldOfStudyController {

    private final FieldOfStudyService fieldOfStudyService;

    public FieldOfStudyController(FieldOfStudyService fieldOfStudyService) {
        this.fieldOfStudyService = fieldOfStudyService;
    }

    @GetMapping("/api/faculties/{facultyId}/fields-of-study")
    public List<FieldOfStudyResponse> listForFaculty(@PathVariable UUID facultyId) {
        return fieldOfStudyService.listForFaculty(facultyId);
    }

    @PostMapping("/api/admin/fields-of-study")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public FieldOfStudyResponse create(@Valid @RequestBody FieldOfStudyRequest request) {
        return fieldOfStudyService.create(request);
    }
}