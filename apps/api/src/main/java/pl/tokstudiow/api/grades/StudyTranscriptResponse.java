package pl.tokstudiow.api.grades;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record StudyTranscriptResponse(
        UUID studyId,
        String fieldOfStudyName,
        @Nullable BigDecimal average,
        List<TranscriptEntryResponse> entries
) {
}