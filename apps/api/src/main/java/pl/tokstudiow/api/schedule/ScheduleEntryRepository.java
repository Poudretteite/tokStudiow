package pl.tokstudiow.api.schedule;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScheduleEntryRepository extends JpaRepository<ScheduleEntry, UUID> {

    /** Cały plan semestru (wszystkie wydziały – sale są wspólne) z danymi potrzebnymi do opisu kolizji. */
    @Query("""
            select s from ScheduleEntry s
            join fetch s.classGroup g
            join fetch g.courseComponent cc
            join fetch cc.course c
            join fetch g.lecturer l
            join fetch l.user
            join fetch s.room
            where g.academicTerm.id = :termId
            order by s.dayOfWeek, s.startTime
            """)
    List<ScheduleEntry> findForTerm(@Param("termId") UUID termId);

    @Query("""
            select s from ScheduleEntry s
            join fetch s.classGroup g
            join fetch g.courseComponent cc
            join fetch cc.course c
            join fetch g.lecturer l
            join fetch l.user
            join fetch s.room
            where g.id = :groupId
            order by s.dayOfWeek, s.startTime
            """)
    List<ScheduleEntry> findForGroup(@Param("groupId") UUID groupId);

    @Query("""
            select s from ScheduleEntry s
            join fetch s.classGroup g
            join fetch g.courseComponent cc
            join fetch cc.course c
            join fetch g.lecturer l
            join fetch l.user
            join fetch s.room
            where g.academicTerm.id = :termId
              and exists (select 1 from GroupEnrollment e where e.classGroup = g and e.study.student.userId = :studentId)
            order by s.dayOfWeek, s.startTime
            """)
    List<ScheduleEntry> findForStudent(@Param("termId") UUID termId, @Param("studentId") UUID studentId);

    /** Plan jednych studiów (student na konkretnym kierunku) – do kolizji przy zapisie do grupy. */
    @Query("""
            select s from ScheduleEntry s
            join fetch s.classGroup g
            where g.academicTerm.id = :termId
              and exists (select 1 from GroupEnrollment e where e.classGroup = g and e.study.id = :studyId)
            """)
    List<ScheduleEntry> findForStudy(@Param("termId") UUID termId, @Param("studyId") UUID studyId);

    @Query("""
            select s from ScheduleEntry s
            join fetch s.classGroup g
            join fetch g.courseComponent cc
            join fetch cc.course c
            join fetch g.lecturer l
            join fetch l.user
            join fetch s.room
            where g.academicTerm.id = :termId and l.userId = :lecturerId
            order by s.dayOfWeek, s.startTime
            """)
    List<ScheduleEntry> findForLecturer(@Param("termId") UUID termId, @Param("lecturerId") UUID lecturerId);

    @Query("""
            select s from ScheduleEntry s
            join fetch s.classGroup g
            join fetch g.courseComponent cc
            join fetch cc.course c
            join fetch c.fieldOfStudy f
            where s.id = :id
            """)
    Optional<ScheduleEntry> findDetailed(@Param("id") UUID id);
}
