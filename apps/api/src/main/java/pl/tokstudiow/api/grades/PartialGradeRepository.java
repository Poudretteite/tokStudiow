package pl.tokstudiow.api.grades;

import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PartialGradeRepository extends JpaRepository<PartialGrade, UUID> {

    @Query("""
            select max(p.attempt) from PartialGrade p
            where p.groupEnrollment.id = :enrollmentId and p.assessmentItem.id = :itemId
            """)
    @Nullable Short findMaxAttempt(@Param("enrollmentId") UUID enrollmentId, @Param("itemId") UUID itemId);

    @Query("""
            select p from PartialGrade p
            join fetch p.groupEnrollment e
            join fetch e.study s
            join fetch s.student st
            join fetch st.user u
            join fetch p.assessmentItem a
            join fetch p.gradeValue v
            where e.classGroup.id = :classGroupId
            order by u.lastName, u.firstName, a.orderIndex, p.attempt
            """)
    List<PartialGrade> findForGroup(@Param("classGroupId") UUID classGroupId);

    @Query("""
            select p from PartialGrade p
            join fetch p.groupEnrollment e
            join fetch e.study s
            join fetch s.student st
            join fetch st.user u
            join fetch p.assessmentItem a
            join fetch p.gradeValue v
            where e.classGroup.id = :classGroupId and st.userId = :studentId
            order by a.orderIndex, p.attempt
            """)
    List<PartialGrade> findForStudentInGroup(@Param("classGroupId") UUID classGroupId,
                                             @Param("studentId") UUID studentId);
}