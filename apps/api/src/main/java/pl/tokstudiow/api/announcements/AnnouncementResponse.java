package pl.tokstudiow.api.announcements;

import java.time.Instant;
import java.util.UUID;

/** Ogłoszenie na tablicy; {@code canEdit}/{@code canDelete} tylko podpowiadają interfejsowi – decyduje serwer. */
public record AnnouncementResponse(
        UUID id,
        String title,
        String content,
        String authorName,
        AnnouncementTarget target,
        Instant createdAt,
        Instant updatedAt,
        boolean canEdit,
        boolean canDelete
) {
}
