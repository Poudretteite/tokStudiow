package pl.tokstudiow.api.university;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AcademicTermRepository extends JpaRepository<AcademicTerm, UUID> {

    List<AcademicTerm> findAllByOrderByStartDateDesc();

    Optional<AcademicTerm> findByCurrentTermTrue();

    boolean existsByNameIgnoreCase(String name);

    @Query("""
            select count(t) > 0 from AcademicTerm t
            where t.startDate <= :endDate and t.endDate >= :startDate
            """)
    boolean existsOverlapping(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Modifying(flushAutomatically = true)
    @Query("update AcademicTerm t set t.currentTerm = false where t.currentTerm = true")
    int clearCurrent();
}