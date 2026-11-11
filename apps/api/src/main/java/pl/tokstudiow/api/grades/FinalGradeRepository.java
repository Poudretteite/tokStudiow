package pl.tokstudiow.api.grades;

import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FinalGradeRepository extends JpaRepository<FinalGrade, UUID> {

    Optional<FinalGrade> findByGroupEnrollmentIdAndAttempt(UUID groupEnrollmentId, FinalAttempt attempt);

    @Query("""
            select f from FinalGrade f
            join fetch f.groupEnrollment e
            join fetch e.study s
            join fetch s.student st
            join fetch st.user u
            join fetch f.gradeValue v
            where e.classGroup.id = :classGroupId
            order by u.lastName, u.firstName
            """)
    List<FinalGrade> findForGroup(@Param("classGroupId") UUID classGroupId);

    @Query("""
            select f from FinalGrade f
            join fetch f.gradeValue v
            join fetch f.groupEnrollment e
            join fetch e.classGroup g
            join fetch g.courseComponent cc
            join fetch cc.course c
            join fetch g.academicTerm t
            join fetch e.study s
            where s.student.userId = :studentId and f.status = :status
            """)
    List<FinalGrade> findForStudent(@Param("studentId") UUID studentId,
                                    @Param("status") FinalGradeStatus status);

    /** Średnia ze wszystkich zatwierdzonych ocen liczbowych; AVG pomija NULL, więc zal/nzal się nie liczą. */
    @Query("""
            select avg(v.numericValue) from FinalGrade f
            join f.gradeValue v
            where f.groupEnrollment.study.id = :studyId and f.status = :status
            """)
    @Nullable Double averageForStudy(@Param("studyId") UUID studyId,
                                     @Param("status") FinalGradeStatus status);

    boolean existsByGroupEnrollmentIdAndStatus(UUID groupEnrollmentId, FinalGradeStatus status);
}