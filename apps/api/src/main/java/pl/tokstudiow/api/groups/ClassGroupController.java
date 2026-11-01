package pl.tokstudiow.api.groups;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
public class ClassGroupController {

    private final ClassGroupService groupService;

    public ClassGroupController(ClassGroupService groupService) {
        this.groupService = groupService;
    }

    @PostMapping("/api/dean-office/groups")
    @PreAuthorize("hasRole('DEAN_OFFICE')")
    @ResponseStatus(HttpStatus.CREATED)
    public ClassGroupResponse create(@Valid @RequestBody CreateClassGroupRequest request) {
        return groupService.create(request);
    }

    @PostMapping("/api/dean-office/groups/{groupId}/enrollments")
    @PreAuthorize("hasRole('DEAN_OFFICE')")
    @ResponseStatus(HttpStatus.CREATED)
    public RosterEntryResponse enroll(@PathVariable UUID groupId, @Valid @RequestBody EnrollRequest request) {
        return groupService.enroll(groupId, request);
    }

    @GetMapping("/api/groups/{groupId}/students")
    public List<RosterEntryResponse> roster(@PathVariable UUID groupId) {
        return groupService.roster(groupId);
    }

    @GetMapping("/api/lecturer/groups")
    @PreAuthorize("hasRole('LECTURER')")
    public List<ClassGroupResponse> myGroupsAsLecturer() {
        return groupService.myGroupsAsLecturer();
    }

    @GetMapping("/api/me/groups")
    @PreAuthorize("hasRole('STUDENT')")
    public List<ClassGroupResponse> myGroupsAsStudent() {
        return groupService.myGroupsAsStudent();
    }
}