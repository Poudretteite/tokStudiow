package pl.tokstudiow.api.announcements;

import org.jspecify.annotations.Nullable;
import pl.tokstudiow.api.groups.ClassGroup;
import pl.tokstudiow.api.university.ComponentType;
import pl.tokstudiow.api.university.Faculty;
import pl.tokstudiow.api.university.FieldOfStudy;

import java.util.UUID;

/**
 * Odbiorca ogłoszenia z nazwą do wyświetlenia. Dla grupy także rodzaj zajęć i numer,
 * z których interfejs składa „Bazy danych, laboratorium, gr. 2”.
 */
public record AnnouncementTarget(
        TargetType type,
        @Nullable UUID id,
        String name,
        @Nullable ComponentType componentType,
        @Nullable Short groupNumber
) {
    static AnnouncementTarget of(ClassGroup group) {
        var component = group.getCourseComponent();
        return new AnnouncementTarget(TargetType.GROUP, group.getId(), component.getCourse().getName(),
                component.getType(), group.getGroupNumber());
    }

    static AnnouncementTarget of(FieldOfStudy field) {
        return new AnnouncementTarget(TargetType.FIELD_OF_STUDY, field.getId(), field.getName(), null, null);
    }

    static AnnouncementTarget of(Faculty faculty) {
        return new AnnouncementTarget(TargetType.FACULTY, faculty.getId(), faculty.getName(), null, null);
    }

    static AnnouncementTarget everyone() {
        return new AnnouncementTarget(TargetType.ALL, null, "Cała uczelnia", null, null);
    }

    static AnnouncementTarget of(Announcement announcement) {
        return switch (announcement.targetType()) {
            case GROUP -> of(announcement.getClassGroup());
            case FIELD_OF_STUDY -> of(announcement.getFieldOfStudy());
            case FACULTY -> of(announcement.getFaculty());
            case ALL -> everyone();
        };
    }
}
