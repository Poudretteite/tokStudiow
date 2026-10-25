package pl.tokstudiow.api.admin;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final UserManagementService userManagement;

    public AdminUserController(UserManagementService userManagement) {
        this.userManagement = userManagement;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
        return UserResponse.from(userManagement.create(request));
    }

    @GetMapping
    public List<UserResponse> list() {
        return userManagement.list().stream().map(UserResponse::from).toList();
    }

    @PostMapping("/{id}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        userManagement.deactivate(id, UUID.fromString(jwt.getSubject()));
    }
}