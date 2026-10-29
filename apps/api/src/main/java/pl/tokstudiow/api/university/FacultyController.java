package pl.tokstudiow.api.university;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
public class FacultyController {

    private final FacultyService facultyService;

    public FacultyController(FacultyService facultyService) {
        this.facultyService = facultyService;
    }

    @GetMapping("/api/faculties")
    public List<FacultyResponse> list() {
        return facultyService.list();
    }

    @GetMapping("/api/faculties/{id}")
    public FacultyResponse get(@PathVariable UUID id) {
        return facultyService.get(id);
    }

    @PostMapping("/api/admin/faculties")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public FacultyResponse create(@Valid @RequestBody FacultyRequest request) {
        return facultyService.create(request);
    }

    @PutMapping("/api/admin/faculties/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public FacultyResponse update(@PathVariable UUID id, @Valid @RequestBody FacultyRequest request) {
        return facultyService.update(id, request);
    }
}