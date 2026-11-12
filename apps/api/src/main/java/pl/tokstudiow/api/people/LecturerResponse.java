package pl.tokstudiow.api.people;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/** Prowadzący do wyboru przy tworzeniu grupy – bez e-maila i innych zbędnych danych. */
public record LecturerResponse(UUID id, String firstName, String lastName, @Nullable String academicTitle) {

    static LecturerResponse from(LecturerProfile profile) {
        return new LecturerResponse(
                profile.getUserId(),
                profile.getUser().getFirstName(),
                profile.getUser().getLastName(),
                profile.getAcademicTitle());
    }
}
