package pl.tokstudiow.api.groups;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record EnrollRequest(@NotNull UUID studyId) {
}