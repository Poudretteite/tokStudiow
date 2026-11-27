package pl.tokstudiow.api.announcements;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import pl.tokstudiow.api.IntegrationTest;
import pl.tokstudiow.api.groups.ClassGroup;
import pl.tokstudiow.api.studies.Study;
import pl.tokstudiow.api.university.AcademicTerm;
import pl.tokstudiow.api.university.ComponentType;
import pl.tokstudiow.api.university.Faculty;
import pl.tokstudiow.api.university.FieldOfStudy;
import pl.tokstudiow.api.university.GradingScaleType;
import pl.tokstudiow.api.user.Role;
import pl.tokstudiow.api.user.User;

import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AnnouncementTest extends IntegrationTest {

    private Faculty science;
    private Faculty medical;
    private FieldOfStudy computerScience;
    private FieldOfStudy medicine;
    private ClassGroup piotrLab;     // Anna zapisana
    private ClassGroup ewaSeminar;

    @BeforeEach
    void setUp() {
        science = fixtures.faculty("Wydział Nauk Ścisłych i Technicznych", "WNŚiT");
        medical = fixtures.faculty("Wydział Medyczny", "WM");
        computerScience = fixtures.fieldOfStudy(science, "Informatyka");
        FieldOfStudy mathematics = fixtures.fieldOfStudy(science, "Matematyka");
        medicine = fixtures.fieldOfStudy(medical, "Kierunek lekarski");

        AcademicTerm term = fixtures.term();
        jdbc.sql("UPDATE academic_terms SET is_current = TRUE WHERE id = :id").param("id", term.getId()).update();
        User piotr = fixtures.lecturer("piotr@test.pl", science);
        User ewa = fixtures.lecturer("ewa@test.pl", science);
        piotrLab = fixtures.group(fixtures.courseComponent(computerScience, "INF-1", ComponentType.LAB,
                GradingScaleType.NUMERIC), term, piotr, 1);
        ewaSeminar = fixtures.group(fixtures.courseComponent(computerScience, "INF-2", ComponentType.SEMINAR,
                GradingScaleType.NUMERIC), term, ewa, 1);

        fixtures.deanOffice("dziekanat@test.pl", science);
        fixtures.deanOffice("dziekanat.wm@test.pl", medical);
        createUser("Ada", "Admin", "admin@test.pl", Role.ADMIN);

        Study anna = fixtures.student("anna@test.pl", "1001", computerScience);
        fixtures.student("jan@test.pl", "1002", mathematics);
        fixtures.student("med@test.pl", "9001", medicine);
        fixtures.enroll(piotrLab, anna);
    }

    // kto co widzi

    @Test
    void lecturerPostsToOwnGroupAndOnlyThatAudienceSeesIt() throws Exception {
        announce("piotr@test.pl", "GROUP", piotrLab.getId(), "Zmiana sali", "Jutro laboratorium w A 102.")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.target.type").value("GROUP"))
                .andExpect(jsonPath("$.target.name").value("Przedmiot INF-1"))
                .andExpect(jsonPath("$.target.componentType").value("LAB"))
                .andExpect(jsonPath("$.authorName").value("Prowadzący piotr@test.pl"))
                .andExpect(jsonPath("$.canEdit").value(true));

        feed("anna@test.pl").andExpect(jsonPath("$[*].title", contains("Zmiana sali")))
                .andExpect(jsonPath("$[0].canEdit").value(false))
                .andExpect(jsonPath("$[0].canDelete").value(false));
        feed("jan@test.pl").andExpect(jsonPath("$", hasSize(0)));
        feed("med@test.pl").andExpect(jsonPath("$", hasSize(0)));
        feed("ewa@test.pl").andExpect(jsonPath("$", hasSize(0)));
        feed("dziekanat@test.pl").andExpect(jsonPath("$", hasSize(1)));
        feed("dziekanat.wm@test.pl").andExpect(jsonPath("$", hasSize(0)));
        feed("admin@test.pl").andExpect(jsonPath("$[0].canDelete").value(true));
    }

    @Test
    void deanOfficeWritesToFieldOfStudyAndWholeFaculty() throws Exception {
        announce("dziekanat@test.pl", "FIELD_OF_STUDY", computerScience.getId(), "Wybór promotorów", "Do 15 grudnia.")
                .andExpect(status().isCreated());
        announce("dziekanat@test.pl", "FACULTY", science.getId(), "Dzień rektorski", "8 grudnia bez zajęć.")
                .andExpect(status().isCreated());

        feed("anna@test.pl").andExpect(jsonPath("$[*].title", contains("Dzień rektorski", "Wybór promotorów")));
        feed("jan@test.pl").andExpect(jsonPath("$[*].title", contains("Dzień rektorski")));
        feed("piotr@test.pl").andExpect(jsonPath("$[*].title", contains("Dzień rektorski")));
        feed("med@test.pl").andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void adminWritesToEveryone() throws Exception {
        announce("admin@test.pl", "ALL", null, "Przerwa techniczna", "W sobotę system będzie niedostępny.")
                .andExpect(status().isCreated());

        for (String email : new String[]{"anna@test.pl", "med@test.pl", "piotr@test.pl", "dziekanat.wm@test.pl"}) {
            feed(email).andExpect(jsonPath("$[*].title", contains("Przerwa techniczna")));
        }
    }

    // kto do kogo może pisać

    @Test
    void lecturerWritesOnlyToOwnGroups() throws Exception {
        announce("piotr@test.pl", "GROUP", ewaSeminar.getId(), "Obce", "x").andExpect(status().isNotFound());
        announce("piotr@test.pl", "FACULTY", science.getId(), "Wydział", "x").andExpect(status().isForbidden());
        announce("piotr@test.pl", "ALL", null, "Wszyscy", "x").andExpect(status().isForbidden());
    }

    @Test
    void deanOfficeCannotWriteToOtherFacultyAndStudentCannotWriteAtAll() throws Exception {
        announce("dziekanat@test.pl", "FIELD_OF_STUDY", medicine.getId(), "Obcy", "x").andExpect(status().isNotFound());
        announce("dziekanat@test.pl", "FACULTY", medical.getId(), "Obcy", "x").andExpect(status().isNotFound());
        announce("dziekanat.wm@test.pl", "GROUP", piotrLab.getId(), "Obca", "x").andExpect(status().isNotFound());
        announce("anna@test.pl", "GROUP", piotrLab.getId(), "Od studenta", "x").andExpect(status().isForbidden());
    }

    @Test
    void targetsListOnlyWhatUserMayWriteTo() throws Exception {
        targets("piotr@test.pl")
                .andExpect(jsonPath("$[*].id", contains(piotrLab.getId().toString())));
        targets("dziekanat@test.pl")
                .andExpect(jsonPath("$[*].type", containsInAnyOrder(
                        "FACULTY", "FIELD_OF_STUDY", "FIELD_OF_STUDY", "GROUP", "GROUP")));
        targets("admin@test.pl").andExpect(jsonPath("$[*].type", contains("ALL")));
        targets("anna@test.pl").andExpect(status().isForbidden());
    }

    // poprawki i usuwanie

    @Test
    void onlyAuthorEditsAndAuthorOrAdminDeletes() throws Exception {
        String id = JsonPath.read(announce("piotr@test.pl", "GROUP", piotrLab.getId(), "Kolokwium", "W środę.")
                .andReturn().getResponse().getContentAsString(), "$.id");

        edit("ewa@test.pl", id).andExpect(status().isNotFound());
        edit("dziekanat@test.pl", id).andExpect(status().isNotFound());
        edit("piotr@test.pl", id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Kolokwium – zmiana"))
                .andExpect(jsonPath("$.target.id").value(piotrLab.getId().toString()));

        remove("dziekanat@test.pl", id).andExpect(status().isNotFound());
        remove("admin@test.pl", id).andExpect(status().isNoContent());
        feed("anna@test.pl").andExpect(jsonPath("$", hasSize(0)));

        auditHistory("announcement", id, tokenFor("admin@test.pl"))
                .andExpect(jsonPath("$[*].action", contains("DELETE", "UPDATE", "CREATE")));
    }

    @Test
    void invalidAnnouncementsAreRejectedAndTextIsKeptAsIs() throws Exception {
        announce("piotr@test.pl", "GROUP", piotrLab.getId(), " ", "Treść").andExpect(status().isBadRequest());
        announce("piotr@test.pl", "GROUP", piotrLab.getId(), "Tytuł", "x".repeat(5001)).andExpect(status().isBadRequest());
        announce("piotr@test.pl", "GROUP", null, "Tytuł", "Treść").andExpect(status().isBadRequest());

        // zwykły tekst – interfejs wyświetla go jako tekst, więc znacznik nie zostanie wykonany
        announce("piotr@test.pl", "GROUP", piotrLab.getId(), "<b>Tytuł</b>", "<script>alert(1)</script>")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value("<script>alert(1)</script>"));
    }

    // pomocnicze

    private ResultActions announce(String email, String type, UUID targetId, String title, String content)
            throws Exception {
        String target = targetId == null ? "null" : "\"" + targetId + "\"";
        return mvc.perform(post("/api/announcements")
                .header(AUTHORIZATION, bearer(tokenFor(email)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title": "%s", "content": "%s", "targetType": "%s", "targetId": %s}
                        """.formatted(title, content, type, target)));
    }

    private ResultActions edit(String email, String id) throws Exception {
        return mvc.perform(put("/api/announcements/{id}", id)
                .header(AUTHORIZATION, bearer(tokenFor(email)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title": "Kolokwium – zmiana", "content": "W czwartek."}
                        """));
    }

    private ResultActions remove(String email, String id) throws Exception {
        return mvc.perform(delete("/api/announcements/{id}", id).header(AUTHORIZATION, bearer(tokenFor(email))));
    }

    private ResultActions feed(String email) throws Exception {
        return mvc.perform(get("/api/announcements").header(AUTHORIZATION, bearer(tokenFor(email))));
    }

    private ResultActions targets(String email) throws Exception {
        return mvc.perform(get("/api/announcements/targets").header(AUTHORIZATION, bearer(tokenFor(email))));
    }
}
