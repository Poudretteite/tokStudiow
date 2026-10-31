package pl.tokstudiow.api.university;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

public record RoomResponse(UUID id, String building, String number, @Nullable Short capacity) {

    static RoomResponse from(Room room) {
        return new RoomResponse(room.getId(), room.getBuilding(), room.getNumber(), room.getCapacity());
    }
}