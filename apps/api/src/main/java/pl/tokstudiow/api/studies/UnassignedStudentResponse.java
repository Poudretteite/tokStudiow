package pl.tokstudiow.api.studies;

import pl.tokstudiow.api.people.StudentProfile;

import java.util.UUID;

/** Nowe konto studenta bez kierunku – tylko dane potrzebne do przypisania, bez e-maila. */
public record UnassignedStudentResponse(UUID id, String firstName, String lastName, String albumNumber) {

    static UnassignedStudentResponse from(StudentProfile profile) {
        return new UnassignedStudentResponse(
                profile.getUserId(),
                profile.getUser().getFirstName(),
                profile.getUser().getLastName(),
                profile.getAlbumNumber());
    }
}
