package pl.tokstudiow.api.attendance;

import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record SessionResponse(
        UUID id,
        UUID classGroupId,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        @Nullable UUID roomId,
        boolean cancelled
) {
    static SessionResponse from(ClassSession session) {
        return new SessionResponse(
                session.getId(),
                session.getClassGroup().getId(),
                session.getDate(),
                session.getStartTime(),
                session.getEndTime(),
                session.getRoom() == null ? null : session.getRoom().getId(),
                session.isCancelled());
    }
}