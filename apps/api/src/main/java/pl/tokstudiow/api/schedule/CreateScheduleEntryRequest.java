package pl.tokstudiow.api.schedule;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;
import java.util.UUID;

public record CreateScheduleEntryRequest(
        @NotNull @Min(1) @Max(7) Integer dayOfWeek,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        @NotNull WeekFrequency frequency,
        @NotNull UUID roomId
) {
}
