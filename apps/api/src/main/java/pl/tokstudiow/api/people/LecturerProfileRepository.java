package pl.tokstudiow.api.people;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface LecturerProfileRepository extends JpaRepository<LecturerProfile, UUID> {

    @Query("""
            select l from LecturerProfile l
            join fetch l.user u
            where l.faculty.id = :facultyId and u.active = true
            order by u.lastName, u.firstName
            """)
    List<LecturerProfile> findActiveForFaculty(@Param("facultyId") UUID facultyId);
}
