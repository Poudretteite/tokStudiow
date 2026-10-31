package pl.tokstudiow.api.university;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tokstudiow.api.audit.AuditAction;
import pl.tokstudiow.api.audit.AuditService;
import pl.tokstudiow.api.common.ConflictException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RoomService {

    private static final String AUDIT_ENTITY = "room";

    private final RoomRepository rooms;
    private final AuditService audit;

    public RoomService(RoomRepository rooms, AuditService audit) {
        this.rooms = rooms;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<RoomResponse> list() {
        return rooms.findAllByOrderByBuildingAscNumberAsc().stream().map(RoomResponse::from).toList();
    }

    @Transactional
    public RoomResponse create(RoomRequest request) {
        String building = request.building().trim();
        String number = request.number().trim();
        if (rooms.existsByBuildingIgnoreCaseAndNumberIgnoreCase(building, number)) {
            throw new ConflictException("Room already exists in this building");
        }

        Room room = rooms.save(new Room(building, number, request.capacity()));
        audit.record(AUDIT_ENTITY, room.getId(), AuditAction.CREATE, null, snapshot(room));
        return RoomResponse.from(room);
    }

    private static Map<String, Object> snapshot(Room room) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("building", room.getBuilding());
        snapshot.put("number", room.getNumber());
        snapshot.put("capacity", room.getCapacity());
        return snapshot;
    }
}