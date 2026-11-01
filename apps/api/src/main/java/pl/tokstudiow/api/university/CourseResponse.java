package pl.tokstudiow.api.university;

import java.util.List;
import java.util.UUID;

public record CourseResponse(
        UUID id,
        UUID fieldOfStudyId,
        String code,
        String name,
        short ects,
        short semesterNumber,
        List<CourseComponentResponse> components
) {
    static CourseResponse from(Course course) {
        return new CourseResponse(
                course.getId(),
                course.getFieldOfStudy().getId(),
                course.getCode(),
                course.getName(),
                course.getEcts(),
                course.getSemesterNumber(),
                course.getComponents().stream().map(CourseComponentResponse::from).toList());
    }
}