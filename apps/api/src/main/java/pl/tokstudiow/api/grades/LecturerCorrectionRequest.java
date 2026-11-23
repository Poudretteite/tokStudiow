package pl.tokstudiow.api.grades;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Korekta pomyłki przez prowadzącego, w terminie korekt – tylko nowa ocena, bez powodu.
 * Kto, kiedy i jaka zmiana trafia do audytu automatycznie.
 */
public record LecturerCorrectionRequest(@NotNull UUID gradeValueId) {
}
