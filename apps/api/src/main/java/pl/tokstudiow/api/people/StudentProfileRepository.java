package pl.tokstudiow.api.people;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StudentProfileRepository extends JpaRepository<StudentProfile, UUID> {

    boolean existsByAlbumNumber(String albumNumber);
}