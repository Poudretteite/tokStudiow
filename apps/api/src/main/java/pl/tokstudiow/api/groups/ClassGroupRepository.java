package pl.tokstudiow.api.groups;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClassGroupRepository extends JpaRepository<ClassGroup, UUID> {

    boolean existsByCourseComponentIdAndAcademicTermIdAndGroupNumber(
            UUID courseComponentId, UUID academicTermId, short groupNumber);

    /** Blokuje wiersz grupy do końca transakcji – chroni limit miejsc przy równoczesnych zapisach. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from ClassGroup g where g.id = :id")
    Optional<ClassGroup> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
            select g from ClassGroup g
            join fetch g.courseComponent cc
            join fetch cc.course c
            join fetch c.fieldOfStudy f
            join fetch g.academicTerm t
            join fetch g.lecturer l
            join fetch l.user
            where g.id = :id
            """)
    Optional<ClassGroup> findDetailed(@Param("id") UUID id);

    @Query("""
            select g from ClassGroup g
            join fetch g.courseComponent cc
            join fetch cc.course c
            join fetch g.academicTerm t
            join fetch g.lecturer l
            join fetch l.user
            where l.userId = :lecturerId
            order by t.startDate desc, c.name, g.groupNumber
            """)
    List<ClassGroup> findForLecturer(@Param("lecturerId") UUID lecturerId);

    @Query("""
            select g from ClassGroup g
            join fetch g.courseComponent cc
            join fetch cc.course c
            join fetch g.academicTerm t
            join fetch g.lecturer l
            join fetch l.user
            where exists (
                select 1 from GroupEnrollment e
                where e.classGroup = g and e.study.student.userId = :studentId
            )
            order by t.startDate desc, c.name, g.groupNumber
            """)
    List<ClassGroup> findForStudent(@Param("studentId") UUID studentId);
}