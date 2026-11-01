package pl.tokstudiow.api.studies;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudyRepository extends JpaRepository<Study, UUID> {

    boolean existsByStudentUserIdAndFieldOfStudyId(UUID studentId, UUID fieldOfStudyId);

    @Query("""
            select s from Study s
            join fetch s.fieldOfStudy f
            join fetch s.student st
            join fetch st.user
            where s.id = :id
            """)
    Optional<Study> findDetailed(@Param("id") UUID id);

    @Query("""
            select s from Study s
            join fetch s.fieldOfStudy f
            join fetch s.student st
            join fetch st.user
            where st.userId = :studentId
            order by s.startYear desc
            """)
    List<Study> findForStudent(@Param("studentId") UUID studentId);

    @Query("""
            select s from Study s
            join fetch s.fieldOfStudy f
            join fetch s.student st
            join fetch st.user u
            where f.faculty.id = :facultyId
            order by u.lastName, u.firstName, f.name
            """)
    List<Study> findForFaculty(@Param("facultyId") UUID facultyId);
}