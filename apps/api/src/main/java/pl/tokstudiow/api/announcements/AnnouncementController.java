package pl.tokstudiow.api.announcements;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/announcements")
public class AnnouncementController {

    private final AnnouncementService service;

    public AnnouncementController(AnnouncementService service) {
        this.service = service;
    }

    /** Tablica zalogowanego użytkownika (każda rola widzi tylko to, co jej dotyczy). */
    @GetMapping
    public List<AnnouncementResponse> feed() {
        return service.feed();
    }

    @GetMapping("/targets")
    @PreAuthorize("hasAnyRole('LECTURER', 'DEAN_OFFICE', 'ADMIN')")
    public List<AnnouncementTarget> targets() {
        return service.targets();
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('LECTURER', 'DEAN_OFFICE', 'ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public AnnouncementResponse create(@Valid @RequestBody AnnouncementRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('LECTURER', 'DEAN_OFFICE', 'ADMIN')")
    public AnnouncementResponse edit(@PathVariable UUID id, @Valid @RequestBody AnnouncementEditRequest request) {
        return service.edit(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('LECTURER', 'DEAN_OFFICE', 'ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
