package pl.tokstudiow.api.announcements;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Tablica ogłoszeń każdej roli jednym zapytaniem. Zawsze widać własne ogłoszenia i te dla całej uczelni.
 * Odbiorców sprawdzają podzapytania {@code exists}, a nie złączenia po opcjonalnych relacjach,
 * żeby ogłoszenie bez grupy nie wypadło z wyniku przez złączenie wewnętrzne.
 */
public interface AnnouncementRepository extends JpaRepository<Announcement, UUID> {

    String FETCH = """
            select a from Announcement a
            join fetch a.author
            left join fetch a.classGroup g
            left join fetch g.courseComponent cc
            left join fetch cc.course
            left join fetch a.fieldOfStudy
            left join fetch a.faculty fa
            """;

    String EVERYONE_OR_MINE = """
            (a.author.id = :userId
             or (a.classGroup is null and a.fieldOfStudy is null and a.faculty is null))
            """;

    /** Student: jego grupy, jego kierunki i wydziały tych kierunków. */
    @Query(FETCH + "where " + EVERYONE_OR_MINE + """
              or exists (select 1 from GroupEnrollment e where e.classGroup = a.classGroup and e.study.student.userId = :userId)
              or exists (select 1 from Study s where s.fieldOfStudy = a.fieldOfStudy and s.student.userId = :userId)
              or exists (select 1 from Study s where s.fieldOfStudy.faculty = a.faculty and s.student.userId = :userId)
            order by a.createdAt desc
            """)
    List<Announcement> findForStudent(@Param("userId") UUID userId, Pageable page);

    /** Prowadzący: jego grupy i jego wydział. */
    @Query(FETCH + "where " + EVERYONE_OR_MINE + """
              or exists (select 1 from ClassGroup cg where cg = a.classGroup and cg.lecturer.userId = :userId)
              or fa.id = :facultyId
            order by a.createdAt desc
            """)
    List<Announcement> findForLecturer(@Param("userId") UUID userId, @Param("facultyId") UUID facultyId, Pageable page);

    /** Dziekanat: wszystko w obrębie wydziału – wydział, jego kierunki i grupy. */
    @Query(FETCH + "where " + EVERYONE_OR_MINE + """
              or fa.id = :facultyId
              or exists (select 1 from FieldOfStudy f where f = a.fieldOfStudy and f.faculty.id = :facultyId)
              or exists (select 1 from ClassGroup cg where cg = a.classGroup
                         and cg.courseComponent.course.fieldOfStudy.faculty.id = :facultyId)
            order by a.createdAt desc
            """)
    List<Announcement> findForDeanOffice(@Param("userId") UUID userId, @Param("facultyId") UUID facultyId,
                                         Pageable page);

    /** Administrator widzi wszystkie (moderacja). */
    @Query(FETCH + "order by a.createdAt desc")
    List<Announcement> findAllForAdmin(Pageable page);
}
