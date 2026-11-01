package pl.tokstudiow.api.groups;

import java.time.Instant;
import java.util.UUID;

public record RosterEntryResponse(
        UUID enrollmentId,
        UUID studyId,
        UUID studentId,
        String firstName,
        String lastName,
        String albumNumber,
        Instant enrolledAt
) {
    static RosterEntryResponse from(GroupEnrollment enrollment) {
        var student = enrollment.getStudy().getStudent();
        return new RosterEntryResponse(
                enrollment.getId(),
                enrollment.getStudy().getId(),
                student.getUserId(),
                student.getUser().getFirstName(),
                student.getUser().getLastName(),
                student.getAlbumNumber(),
                enrollment.getEnrolledAt());
    }
}