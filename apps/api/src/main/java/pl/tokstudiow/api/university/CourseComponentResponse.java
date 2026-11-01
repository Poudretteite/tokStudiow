package pl.tokstudiow.api.university;

import java.util.UUID;

public record CourseComponentResponse(UUID id, ComponentType type, short hours, UUID gradingScaleId) {

    static CourseComponentResponse from(CourseComponent component) {
        return new CourseComponentResponse(
                component.getId(), component.getType(), component.getHours(), component.getGradingScale().getId());
    }
}