package pl.tokstudiow.api.university;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;
import java.util.Optional;

public interface GradingScaleRepository extends JpaRepository<GradingScale, UUID> {

    @Query("select distinct s from GradingScale s left join fetch s.values order by s.name")
    List<GradingScale> findAllWithValues();
    Optional<GradingScale> findFirstByType(GradingScaleType type);
}