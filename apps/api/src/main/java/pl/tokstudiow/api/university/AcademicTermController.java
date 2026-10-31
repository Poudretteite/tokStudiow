package pl.tokstudiow.api.university;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
public class AcademicTermController {

    private final AcademicTermService termService;

    public AcademicTermController(AcademicTermService termService) {
        this.termService = termService;
    }

    @GetMapping("/api/academic-terms")
    public List<AcademicTermResponse> list() {
        return termService.list();
    }

    @GetMapping("/api/academic-terms/current")
    public AcademicTermResponse current() {
        return termService.current();
    }

    @PostMapping("/api/admin/academic-terms")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public AcademicTermResponse create(@Valid @RequestBody AcademicTermRequest request) {
        return termService.create(request);
    }

    @PostMapping("/api/admin/academic-terms/{id}/make-current")
    @PreAuthorize("hasRole('ADMIN')")
    public AcademicTermResponse makeCurrent(@PathVariable UUID id) {
        return termService.makeCurrent(id);
    }
}