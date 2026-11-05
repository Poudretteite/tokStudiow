package pl.tokstudiow.api.attendance;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record RecordAttendanceRequest(
        @NotEmpty @Size(max = 500) List<@Valid @NotNull AttendanceEntryRequest> entries
) {
}