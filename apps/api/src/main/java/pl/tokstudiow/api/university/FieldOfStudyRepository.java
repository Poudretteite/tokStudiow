package pl.tokstudiow.api.university;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FieldOfStudyRepository extends JpaRepository<FieldOfStudy, UUID> {

    List<FieldOfStudy> findByFacultyIdOrderByNameAscLevelAscModeAsc(UUID facultyId);

    boolean existsByFacultyIdAndNameIgnoreCaseAndLevelAndMode(
            UUID facultyId, String name, StudyLevel level, StudyMode mode);
}