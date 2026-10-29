package pl.tokstudiow.api.university;

import java.util.UUID;

public record FacultyResponse(UUID id, String name, String shortName) {

    static FacultyResponse from(Faculty faculty) {
        return new FacultyResponse(faculty.getId(), faculty.getName(), faculty.getShortName());
    }
}