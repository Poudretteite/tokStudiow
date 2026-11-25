package pl.tokstudiow.api.schedule;

import java.util.List;

/** 409 z listą wszystkich kolizji naraz, żeby dziekanat od razu widział, co blokuje termin. */
public class ScheduleConflictException extends RuntimeException {

    private final List<ScheduleConflict> conflicts;

    public ScheduleConflictException(List<ScheduleConflict> conflicts) {
        super("Schedule conflict");
        this.conflicts = List.copyOf(conflicts);
    }

    public List<ScheduleConflict> getConflicts() {
        return conflicts;
    }
}
