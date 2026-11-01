package pl.tokstudiow.api.people;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DeanOfficeProfileRepository extends JpaRepository<DeanOfficeProfile, UUID> {
}