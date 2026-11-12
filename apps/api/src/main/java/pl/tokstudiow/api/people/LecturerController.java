package pl.tokstudiow.api.people;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class LecturerController {

    private final LecturerService lecturerService;

    public LecturerController(LecturerService lecturerService) {
        this.lecturerService = lecturerService;
    }

    @GetMapping("/api/dean-office/lecturers")
    @PreAuthorize("hasRole('DEAN_OFFICE')")
    public List<LecturerResponse> listForMyFaculty() {
        return lecturerService.listForMyFaculty();
    }
}
