package pl.tokstudiow.api.groups;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** Zapis wielu studentów do grupy jednym żądaniem – wszystko albo nic. */
public record BatchEnrollRequest(@NotEmpty @Size(max = 500) List<@NotNull UUID> studyIds) {
}
