package pl.tokstudiow.api.schedule;

import pl.tokstudiow.api.university.ComponentType;

import java.time.LocalTime;
import java.util.UUID;

/** Termin w planie z opisem zajęć – tyle, ile potrzeba do narysowania planu bez kolejnych zapytań. */
public record ScheduleEntryResponse(
        UUID id,
        UUID groupId,
        String courseCode,
        String courseName,
        ComponentType componentType,
        short groupNumber,
        String lecturerName,
        int dayOfWeek,
        LocalTime startTime,
        LocalTime endTime,
        WeekFrequency frequency,
        UUID roomId,
        String roomLabel
) {
    static ScheduleEntryResponse from(ScheduleEntry entry) {
        var group = entry.getClassGroup();
        var component = group.getCourseComponent();
        var lecturer = group.getLecturer();
        String title = lecturer.getAcademicTitle();
        String name = lecturer.getUser().getFirstName() + " " + lecturer.getUser().getLastName();
        var room = entry.getRoom();
        return new ScheduleEntryResponse(
                entry.getId(),
                group.getId(),
                component.getCourse().getCode(),
                component.getCourse().getName(),
                component.getType(),
                group.getGroupNumber(),
                title == null || title.isBlank() ? name : title + " " + name,
                entry.getDayOfWeek(),
                entry.getStartTime(),
                entry.getEndTime(),
                entry.getFrequency(),
                room.getId(),
                room.getBuilding() + ", s. " + room.getNumber());
    }
}
