package pl.tokstudiow.api.groups;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface GroupEnrollmentRepository extends JpaRepository<GroupEnrollment, UUID> {

    long countByClassGroupId(UUID classGroupId);

    boolean existsByClassGroupIdAndStudyId(UUID classGroupId, UUID studyId);
    boolean existsByClassGroupIdAndStudyStudentUserId(UUID classGroupId, UUID studentId);

    @Query("""
            select count(e) > 0 from GroupEnrollment e
            where e.study.id = :studyId
              and e.classGroup.courseComponent.id = :courseComponentId
              and e.classGroup.academicTerm.id = :academicTermId
            """)
    boolean existsInComponentAndTerm(@Param("studyId") UUID studyId,
                                     @Param("courseComponentId") UUID courseComponentId,
                                     @Param("academicTermId") UUID academicTermId);

    @Query("""
            select e from GroupEnrollment e
            join fetch e.study s
            join fetch s.student st
            join fetch st.user u
            where e.classGroup.id = :classGroupId
            order by u.lastName, u.firstName
            """)
    List<GroupEnrollment> findRoster(@Param("classGroupId") UUID classGroupId);
}