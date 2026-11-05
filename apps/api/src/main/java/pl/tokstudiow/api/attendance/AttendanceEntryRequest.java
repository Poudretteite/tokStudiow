package pl.tokstudiow.api.attendance;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AttendanceEntryRequest(@NotNull UUID enrollmentId, @NotNull AttendanceStatus status) {
}