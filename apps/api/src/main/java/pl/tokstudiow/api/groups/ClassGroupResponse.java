package pl.tokstudiow.api.groups;

import org.jspecify.annotations.Nullable;
import pl.tokstudiow.api.university.ComponentType;

import java.util.UUID;

public record ClassGroupResponse(
        UUID id,
        UUID courseId,
        String courseCode,
        String courseName,
        ComponentType componentType,
        UUID gradingScaleId,
        UUID academicTermId,
        String academicTermName,
        UUID lecturerId,
        String lecturerFirstName,
        String lecturerLastName,
        short groupNumber,
        @Nullable Short capacity
) {
    static ClassGroupResponse from(ClassGroup group) {
        var component = group.getCourseComponent();
        var course = component.getCourse();
        var term = group.getAcademicTerm();
        var lecturer = group.getLecturer();
        return new ClassGroupResponse(
                group.getId(),
                course.getId(),
                course.getCode(),
                course.getName(),
                component.getType(),
                component.getGradingScale().getId(),
                term.getId(),
                term.getName(),
                lecturer.getUserId(),
                lecturer.getUser().getFirstName(),
                lecturer.getUser().getLastName(),
                group.getGroupNumber(),
                group.getCapacity());
    }
}