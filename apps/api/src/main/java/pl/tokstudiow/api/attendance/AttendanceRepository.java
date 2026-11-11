package pl.tokstudiow.api.attendance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttendanceRepository extends JpaRepository<Attendance, UUID> {

    Optional<Attendance> findByClassSessionIdAndGroupEnrollmentId(UUID classSessionId, UUID groupEnrollmentId);

    @Query("""
            select a from Attendance a
            join fetch a.groupEnrollment e
            join fetch e.study s
            join fetch s.student st
            join fetch st.user u
            where a.classSession.id = :sessionId
            order by u.lastName, u.firstName
            """)
    List<Attendance> findForSession(@Param("sessionId") UUID sessionId);

    @Query("""
            select a from Attendance a
            join fetch a.classSession cs
            join a.groupEnrollment e
            where cs.classGroup.id = :classGroupId and e.study.student.userId = :studentId
            order by cs.date, cs.startTime
            """)
    List<Attendance> findForStudentInGroup(@Param("classGroupId") UUID classGroupId,
                                           @Param("studentId") UUID studentId);

    /** Wszystkie obecności grupy jednym zapytaniem – do tabeli „studenci × zajęcia”. */
    @Query("select a from Attendance a where a.classSession.classGroup.id = :classGroupId")
    List<Attendance> findForGroup(@Param("classGroupId") UUID classGroupId);
}