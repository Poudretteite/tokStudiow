package pl.tokstudiow.api.auth;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import pl.tokstudiow.api.IntegrationTest;
import pl.tokstudiow.api.university.Faculty;
import pl.tokstudiow.api.university.FieldOfStudy;
import pl.tokstudiow.api.user.Role;
import pl.tokstudiow.api.user.User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpHeaders.CACHE_CONTROL;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Nowe hasło startowe od administratora (dowolne konto) i od dziekanatu (studenci własnego wydziału). */
class PasswordResetTest extends IntegrationTest {

    private User admin;
    private User lecturer;
    private String annaStudyId;
    private String foreignStudyId;

    @BeforeEach
    void setUp() {
        admin = createUser("Ada", "Admin", "admin@test.pl", Role.ADMIN);
        Faculty science = fixtures.faculty("Wydział Nauk Ścisłych i Technicznych", "WNŚiT");
        Faculty medical = fixtures.faculty("Wydział Medyczny", "WM");
        FieldOfStudy computerScience = fixtures.fieldOfStudy(science, "Informatyka");
        FieldOfStudy medicine = fixtures.fieldOfStudy(medical, "Kierunek lekarski");

        lecturer = fixtures.lecturer("piotr@test.pl", science);
        fixtures.deanOffice("dziekanat@test.pl", science);
        annaStudyId = fixtures.student("anna@test.pl", "1001", computerScience).getId().toString();
        foreignStudyId = fixtures.student("med@test.pl", "9001", medicine).getId().toString();
    }

    // administrator

    @Test
    void adminResetGivesStartPasswordThatMustBeChanged() throws Exception {
        String oldSession = refreshTokenOf("piotr@test.pl");

        String json = adminReset(lecturer.getId().toString())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("piotr@test.pl"))
                .andExpect(header().string(CACHE_CONTROL, containsString("no-store")))
                .andReturn().getResponse().getContentAsString();
        String startPassword = JsonPath.read(json, "$.initialPassword");
        assertThat(startPassword).hasSize(16);

        login("piotr@test.pl", PASSWORD).andExpect(status().isUnauthorized());
        String token = JsonPath.read(login("piotr@test.pl", startPassword).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.accessToken");
        mvc.perform(get("/api/me").header(AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.mustChangePassword").value(true));

        // reset kończy wszystkie dotychczasowe sesje
        refresh(oldSession).andExpect(status().isUnauthorized());
    }

    @Test
    void resetIsAuditedWithoutPassword() throws Exception {
        String json = adminReset(lecturer.getId().toString()).andReturn().getResponse().getContentAsString();
        String startPassword = JsonPath.read(json, "$.initialPassword");

        auditHistory("user", lecturer.getId().toString(), tokenFor("admin@test.pl"))
                .andExpect(jsonPath("$[0].newValue.event").value("PASSWORD_RESET"))
                .andExpect(jsonPath("$[0].actorId").value(admin.getId().toString()));
        String raw = jdbc.sql("SELECT CAST(new_value AS text) FROM audit_log WHERE entity_id = :id")
                .param("id", lecturer.getId()).query(String.class).single();
        assertThat(raw).doesNotContain(startPassword).doesNotContain("$2a$");
    }

    @Test
    void adminCannotResetOwnOrDeactivatedOrUnknownAccount() throws Exception {
        adminReset(admin.getId().toString()).andExpect(status().isBadRequest());
        adminReset("00000000-0000-0000-0000-000000000000").andExpect(status().isNotFound());

        mvc.perform(post("/api/admin/users/{id}/deactivate", lecturer.getId())
                .header(AUTHORIZATION, bearer(tokenFor("admin@test.pl"))));
        adminReset(lecturer.getId().toString()).andExpect(status().isBadRequest());
    }

    @Test
    void onlyAdminUsesAdminReset() throws Exception {
        mvc.perform(post("/api/admin/users/{id}/reset-password", lecturer.getId())
                        .header(AUTHORIZATION, bearer(tokenFor("dziekanat@test.pl"))))
                .andExpect(status().isForbidden());
    }

    // dziekanat

    @Test
    void deanOfficeResetsPasswordOfOwnFacultyStudent() throws Exception {
        String json = deanReset("dziekanat@test.pl", annaStudyId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("anna@test.pl"))
                .andReturn().getResponse().getContentAsString();

        login("anna@test.pl", JsonPath.read(json, "$.initialPassword")).andExpect(status().isOk());
    }

    @Test
    void deanOfficeCannotResetStudentOfOtherFacultyAndLecturerCannotResetAtAll() throws Exception {
        deanReset("dziekanat@test.pl", foreignStudyId).andExpect(status().isNotFound());
        deanReset("piotr@test.pl", annaStudyId).andExpect(status().isForbidden());
        login("anna@test.pl", PASSWORD).andExpect(status().isOk());
    }

    // pomocnicze

    private ResultActions adminReset(String userId) throws Exception {
        return mvc.perform(post("/api/admin/users/{id}/reset-password", userId)
                .header(AUTHORIZATION, bearer(tokenFor("admin@test.pl"))));
    }

    private ResultActions deanReset(String email, String studyId) throws Exception {
        return mvc.perform(post("/api/dean-office/studies/{id}/reset-password", studyId)
                .header(AUTHORIZATION, bearer(tokenFor(email))));
    }

    private String refreshTokenOf(String email) throws Exception {
        return JsonPath.read(login(email, PASSWORD).andReturn().getResponse().getContentAsString(), "$.refreshToken");
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\": \"" + refreshToken + "\"}"));
    }
}
