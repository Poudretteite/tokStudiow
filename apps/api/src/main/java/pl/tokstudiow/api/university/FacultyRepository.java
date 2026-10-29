package pl.tokstudiow.api.university;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface FacultyRepository extends JpaRepository<Faculty, UUID> {

    boolean existsByNameIgnoreCaseOrShortNameIgnoreCase(String name, String shortName);

    @Query("""
            select count(f) > 0 from Faculty f
            where f.id <> :id
              and (lower(f.name) = lower(:name) or lower(f.shortName) = lower(:shortName))
            """)
    boolean existsOtherWithNameOrShortName(@Param("id") UUID id,
                                           @Param("name") String name,
                                           @Param("shortName") String shortName);
}