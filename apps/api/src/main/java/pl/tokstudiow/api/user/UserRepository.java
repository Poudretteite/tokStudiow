package pl.tokstudiow.api.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    /** Które z podanych adresów (małymi literami) są już zajęte – jedno zapytanie dla całego importu. */
    @Query("select lower(u.email) from User u where lower(u.email) in :emails")
    List<String> findExistingEmails(@Param("emails") Collection<String> emailsLowerCase);
}
