package pl.tokstudiow.api.university;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
public class CourseController {

    private final CourseService courseService;

    public CourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    @GetMapping("/api/fields-of-study/{fieldOfStudyId}/courses")
    public List<CourseResponse> listForFieldOfStudy(@PathVariable UUID fieldOfStudyId) {
        return courseService.listForFieldOfStudy(fieldOfStudyId);
    }

    @GetMapping("/api/courses/{id}")
    public CourseResponse get(@PathVariable UUID id) {
        return courseService.get(id);
    }

    @PostMapping("/api/admin/courses")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public CourseResponse create(@Valid @RequestBody CourseRequest request) {
        return courseService.create(request);
    }
}