package pl.tokstudiow.api.schedule;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record CreateScheduleEntryRequest(
        @NotNull @Min(1) @Max(7) Integer dayOfWeek,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        @NotNull WeekFrequency frequency,
        @NotNull UUID roomId,
        // tylko dla SELECTED_WEEKS: numery tygodni semestru
        @Nullable @Size(max = 60) List<@NotNull @Min(1) @Max(60) Integer> weeks
) {
}
