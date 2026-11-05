package pl.tokstudiow.api.attendance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ClassSessionRepository extends JpaRepository<ClassSession, UUID> {

    List<ClassSession> findByClassGroupIdOrderByDateAscStartTimeAsc(UUID classGroupId);
}