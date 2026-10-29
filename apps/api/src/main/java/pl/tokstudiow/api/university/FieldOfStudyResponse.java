package pl.tokstudiow.api.university;

import java.util.UUID;

public record FieldOfStudyResponse(
        UUID id,
        UUID facultyId,
        String name,
        StudyLevel level,
        StudyMode mode,
        short semestersCount
) {
    static FieldOfStudyResponse from(FieldOfStudy field) {
        return new FieldOfStudyResponse(
                field.getId(),
                field.getFaculty().getId(),
                field.getName(),
                field.getLevel(),
                field.getMode(),
                field.getSemestersCount());
    }
}