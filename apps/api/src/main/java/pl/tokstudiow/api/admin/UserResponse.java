package pl.tokstudiow.api.admin;

import org.jspecify.annotations.Nullable;
import pl.tokstudiow.api.user.Role;
import pl.tokstudiow.api.user.User;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String firstName,
        String lastName,
        String email,
        Role role,
        boolean active,
        Instant createdAt,
        @Nullable String albumNumber,
        @Nullable UUID facultyId
) {
    public static UserResponse from(User user, @Nullable String albumNumber, @Nullable UUID facultyId) {
        return new UserResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getRole(),
                user.isActive(),
                user.getCreatedAt(),
                albumNumber,
                facultyId);
    }
}