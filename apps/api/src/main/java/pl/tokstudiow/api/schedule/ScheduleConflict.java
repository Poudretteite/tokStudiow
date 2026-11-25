package pl.tokstudiow.api.schedule;

import org.jspecify.annotations.Nullable;

/**
 * Powód odrzucenia terminu: ta sama grupa, zajęta sala, zajęty prowadzący albo studenci,
 * którzy w tym czasie mają inne zajęcia (wtedy {@code studentCount} – ilu).
 */
public record ScheduleConflict(Type type, ScheduleEntryResponse entry, @Nullable Integer studentCount) {

    public enum Type {
        GROUP,
        ROOM,
        LECTURER,
        STUDENTS
    }
}
