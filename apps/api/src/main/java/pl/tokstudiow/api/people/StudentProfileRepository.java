package pl.tokstudiow.api.people;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface StudentProfileRepository extends JpaRepository<StudentProfile, UUID> {

    boolean existsByAlbumNumber(String albumNumber);

    /** Aktywne konta studentów, którym dziekanat nie przypisał jeszcze żadnego kierunku. */
    @Query("""
            select p from StudentProfile p
            join fetch p.user u
            where u.active = true
              and not exists (select 1 from Study s where s.student = p)
            order by u.lastName, u.firstName
            """)
    List<StudentProfile> findActiveWithoutStudies();
}
