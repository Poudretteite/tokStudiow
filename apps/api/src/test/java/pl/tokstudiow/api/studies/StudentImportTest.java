package pl.tokstudiow.api.studies;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import pl.tokstudiow.api.IntegrationTest;
import pl.tokstudiow.api.university.Faculty;
import pl.tokstudiow.api.university.FieldOfStudy;
import pl.tokstudiow.api.user.Role;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StudentImportTest extends IntegrationTest {

    private FieldOfStudy computerScience;
    private FieldOfStudy medicine;
    private String deanToken;
    private String lecturerToken;
    private String annaStudyId;
    private String janStudyId;

    @BeforeEach
    void setUp() throws Exception {
        Faculty science = fixtures.faculty("Wydział Nauk Ścisłych i Technicznych", "WNŚiT");
        Faculty medical = fixtures.faculty("Wydział Medyczny", "WM");
        computerScience = fixtures.fieldOfStudy(science, "Informatyka");   // 7 semestrów
        medicine = fixtures.fieldOfStudy(medical, "Kierunek lekarski");

        fixtures.deanOffice("dziekanat@test.pl", science);
        fixtures.lecturer("piotr@test.pl", science);
        annaStudyId = fixtures.student("anna@test.pl", "1001", computerScience).getId().toString();  // semestr 3
        janStudyId = fixtures.student("jan@test.pl", "1002", computerScience).getId().toString();

        deanToken = tokenFor("dziekanat@test.pl");
        lecturerToken = tokenFor("piotr@test.pl");
    }

    // import

    @Test
    void deanOfficeImportsStudentsWithGeneratedEmailWhoCanLogIn() throws Exception {
        String json = importStudents(deanToken, computerScience, 1,
                row("Oliwia", "Mazur", "2001"),
                row("Łukasz", "Żak", "2002"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].email").value("om2001@tokstudiow.pl"))
                .andExpect(jsonPath("$[1].email").value("lz2002@tokstudiow.pl"))
                .andReturn().getResponse().getContentAsString();

        String password = JsonPath.read(json, "$[0].initialPassword");
        assertThat(password).hasSize(16);
        login("om2001@tokstudiow.pl", password).andExpect(status().isOk());

        mvc.perform(get("/api/dean-office/studies").header(AUTHORIZATION, bearer(deanToken)))
                .andExpect(jsonPath("$[?(@.albumNumber == '2002')].currentSemester").value(1));
    }

    @Test
    void importedAccountsAreAuditedWithoutPassword() throws Exception {
        String json = importStudents(deanToken, computerScience, 1, row("Oliwia", "Mazur", "2001"))
                .andReturn().getResponse().getContentAsString();
        String userId = JsonPath.read(json, "$[0].studentId");

        String rawEntry = jdbc.sql("SELECT CAST(new_value AS text) FROM audit_log WHERE entity_id = :id")
                .param("id", UUID.fromString(userId))
                .query(String.class)
                .single();
        assertThat(rawEntry).contains("om2001@tokstudiow.pl").doesNotContain("password").doesNotContain("$2a$");
    }

    @Test
    void importIsAllOrNothingAndListsEveryBadRow() throws Exception {
        fixtures.user("Adam", "Dąb", "ad2004@tokstudiow.pl", Role.LECTURER);   // adres zajęty przez inne konto

        importStudents(deanToken, computerScience, 1,
                row("Oliwia", "Mazur", "2001"),
                row("Ola", "Mróz", "2001"),       // ten sam album w pliku
                row("Jan", "Drugi", "1002"),      // album już w systemie
                row("Anna", "Dudek", "2004"))     // wygenerowany adres ad2004@ już zajęty
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.rows", hasSize(3)))
                .andExpect(jsonPath("$.rows[*].row", contains(2, 3, 4)))
                .andExpect(jsonPath("$.rows[*].field", contains("albumNumber", "albumNumber", "albumNumber")))
                .andExpect(jsonPath("$.rows[2].message").value("Email already in use"));

        login("om2001@tokstudiow.pl", PASSWORD).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/dean-office/studies").header(AUTHORIZATION, bearer(deanToken)))
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void importChecksFieldOfStudyOwnerAndSemester() throws Exception {
        importStudents(deanToken, medicine, 1, row("A", "B", "3001")).andExpect(status().isNotFound());
        importStudents(deanToken, computerScience, 8, row("A", "B", "3001")).andExpect(status().isBadRequest());
        importStudents(deanToken, computerScience, 1, row("A", "B", "30<01>")).andExpect(status().isBadRequest());
    }

    @Test
    void onlyDeanOfficeCanImportStudents() throws Exception {
        importStudents(lecturerToken, computerScience, 1, row("A", "B", "3001")).andExpect(status().isForbidden());
        createUser("Anna", "Studentka", "s@test.pl", Role.STUDENT);
        importStudents(tokenFor("s@test.pl"), computerScience, 1, row("A", "B", "3001"))
                .andExpect(status().isForbidden());
    }

    // zmiany dla wielu studentów naraz

    @Test
    void deanOfficePromotesManyStudentsToNextSemester() throws Exception {
        batchUpdate(deanToken, """
                {"studyIds": ["%s", "%s"], "promote": true}
                """.formatted(annaStudyId, janStudyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].currentSemester", contains(4, 4)));
    }

    @Test
    void deanOfficeSetsStatusForManyStudents() throws Exception {
        batchUpdate(deanToken, """
                {"studyIds": ["%s", "%s"], "promote": false, "status": "DEAN_LEAVE"}
                """.formatted(annaStudyId, janStudyId))
                .andExpect(jsonPath("$[*].status", contains("DEAN_LEAVE", "DEAN_LEAVE")))
                .andExpect(jsonPath("$[*].currentSemester", contains(3, 3)));
    }

    @Test
    void batchUpdateIsAllOrNothing() throws Exception {
        String lastSemester = fixtures.student("ola@test.pl", "1003", computerScience).getId().toString();
        batchUpdate(deanToken, """
                {"studyIds": ["%s"], "promote": true}
                """.formatted(lastSemester));    // semestr 3 → 4: przechodzi
        for (int i = 0; i < 3; i++) {
            batchUpdate(deanToken, """
                    {"studyIds": ["%s"], "promote": true}
                    """.formatted(lastSemester));    // 5, 6, 7
        }

        // Ola na ostatnim semestrze – nikt nie zostaje przeniesiony, także Anna
        batchUpdate(deanToken, """
                {"studyIds": ["%s", "%s"], "promote": true}
                """.formatted(annaStudyId, lastSemester))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/studies/{id}", annaStudyId).header(AUTHORIZATION, bearer(deanToken)))
                .andExpect(jsonPath("$.currentSemester").value(3));
    }

    @Test
    void batchUpdateNeedsExactlyOneChangeAndOwnStudents() throws Exception {
        batchUpdate(deanToken, """
                {"studyIds": ["%s"], "promote": true, "status": "ACTIVE"}
                """.formatted(annaStudyId)).andExpect(status().isBadRequest());
        batchUpdate(deanToken, """
                {"studyIds": ["%s"], "promote": false}
                """.formatted(annaStudyId)).andExpect(status().isBadRequest());

        String foreign = fixtures.student("med@test.pl", "9001", medicine).getId().toString();
        batchUpdate(deanToken, """
                {"studyIds": ["%s"], "promote": true}
                """.formatted(foreign)).andExpect(status().isNotFound());
    }

    // pomocnicze

    private ResultActions importStudents(String token, FieldOfStudy field, int semester, String... rows)
            throws Exception {
        return mvc.perform(post("/api/dean-office/students/import")
                .header(AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fieldOfStudyId": "%s", "startYear": 2026, "currentSemester": %d, "students": [%s]}
                        """.formatted(field.getId(), semester, String.join(", ", rows))));
    }

    private static String row(String firstName, String lastName, String albumNumber) {
        return """
                {"firstName": "%s", "lastName": "%s", "albumNumber": "%s"}
                """.formatted(firstName, lastName, albumNumber);
    }

    private ResultActions batchUpdate(String token, String body) throws Exception {
        return mvc.perform(post("/api/dean-office/studies/batch-update")
                .header(AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}
