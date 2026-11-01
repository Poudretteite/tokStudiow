package pl.tokstudiow.api.people;

import org.jspecify.annotations.Nullable;
import pl.tokstudiow.api.user.Role;

import java.util.UUID;

public record MeResponse(
        UUID id,
        String firstName,
        String lastName,
        String email,
        Role role,
        @Nullable String albumNumber,
        @Nullable UUID facultyId,
        @Nullable String academicTitle
) {
}