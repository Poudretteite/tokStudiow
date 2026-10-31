package pl.tokstudiow.api.university;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RoomRepository extends JpaRepository<Room, UUID> {

    List<Room> findAllByOrderByBuildingAscNumberAsc();

    boolean existsByBuildingIgnoreCaseAndNumberIgnoreCase(String building, String number);
}