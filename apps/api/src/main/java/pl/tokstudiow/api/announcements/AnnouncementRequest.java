package pl.tokstudiow.api.announcements;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Nowe ogłoszenie. Treść to zwykły tekst – interfejs nigdy nie wstawia jej jako HTML,
 * więc wpisany znacznik {@code <script>} pokaże się jako tekst (brak XSS).
 */
public record AnnouncementRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 5000) String content,
        @NotNull TargetType targetType,
        // grupa, kierunek albo wydział; dla całej uczelni pusty
        @Nullable UUID targetId
) {
}
