package pl.tokstudiow.api.schedule;

import jakarta.validation.Valid;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
public class ScheduleController {

    private final ScheduleService schedule;

    public ScheduleController(ScheduleService schedule) {
        this.schedule = schedule;
    }

    @GetMapping("/api/me/schedule")
    @PreAuthorize("hasRole('STUDENT')")
    public ScheduleResponse mySchedule(@RequestParam(required = false) @Nullable UUID termId) {
        return schedule.forStudent(termId);
    }

    @GetMapping("/api/lecturer/schedule")
    @PreAuthorize("hasRole('LECTURER')")
    public ScheduleResponse lecturerSchedule(@RequestParam(required = false) @Nullable UUID termId) {
        return schedule.forLecturer(termId);
    }

    @GetMapping("/api/dean-office/groups/{groupId}/schedule")
    @PreAuthorize("hasRole('DEAN_OFFICE')")
    public List<ScheduleEntryResponse> groupSchedule(@PathVariable UUID groupId) {
        return schedule.groupSchedule(groupId);
    }

    @PostMapping("/api/dean-office/groups/{groupId}/schedule")
    @PreAuthorize("hasRole('DEAN_OFFICE')")
    @ResponseStatus(HttpStatus.CREATED)
    public ScheduleEntryResponse add(@PathVariable UUID groupId, @Valid @RequestBody CreateScheduleEntryRequest request) {
        return schedule.add(groupId, request);
    }

    @DeleteMapping("/api/dean-office/schedule-entries/{entryId}")
    @PreAuthorize("hasRole('DEAN_OFFICE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable UUID entryId) {
        schedule.remove(entryId);
    }
}
