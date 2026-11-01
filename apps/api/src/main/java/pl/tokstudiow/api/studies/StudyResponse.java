package pl.tokstudiow.api.studies;

import java.util.UUID;

public record StudyResponse(
        UUID id,
        UUID studentId,
        String studentFirstName,
        String studentLastName,
        String albumNumber,
        UUID fieldOfStudyId,
        String fieldOfStudyName,
        UUID facultyId,
        short startYear,
        short currentSemester,
        StudyStatus status
) {
    static StudyResponse from(Study study) {
        var student = study.getStudent();
        var field = study.getFieldOfStudy();
        return new StudyResponse(
                study.getId(),
                student.getUserId(),
                student.getUser().getFirstName(),
                student.getUser().getLastName(),
                student.getAlbumNumber(),
                field.getId(),
                field.getName(),
                field.getFaculty().getId(),
                study.getStartYear(),
                study.getCurrentSemester(),
                study.getStatus());
    }
}