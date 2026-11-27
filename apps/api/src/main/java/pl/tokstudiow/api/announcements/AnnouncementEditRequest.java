package pl.tokstudiow.api.announcements;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Poprawka ogłoszenia – bez zmiany odbiorców. */
public record AnnouncementEditRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 5000) String content
) {
}
