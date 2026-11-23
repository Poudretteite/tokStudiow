package pl.tokstudiow.api.studies;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Jedna zmiana dla wielu studentów naraz: przeniesienie na kolejny semestr
 * albo ustawienie statusu (np. urlop dziekański). Dokładnie jedno z dwóch.
 */
public record StudyBatchUpdateRequest(
        @NotEmpty @Size(max = 500) List<@NotNull UUID> studyIds,
        boolean promote,
        @Nullable StudyStatus status
) {
}
