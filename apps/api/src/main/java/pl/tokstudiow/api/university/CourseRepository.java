package pl.tokstudiow.api.university;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CourseRepository extends JpaRepository<Course, UUID> {

    boolean existsByCodeIgnoreCase(String code);

    @Query("""
            select distinct c from Course c
            left join fetch c.components
            where c.fieldOfStudy.id = :fieldOfStudyId
            order by c.semesterNumber, c.name
            """)
    List<Course> findByFieldOfStudyWithComponents(@Param("fieldOfStudyId") UUID fieldOfStudyId);

    @Query("select c from Course c left join fetch c.components where c.id = :id")
    Optional<Course> findWithComponents(@Param("id") UUID id);
}