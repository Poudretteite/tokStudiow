package pl.tokstudiow.api.university;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import pl.tokstudiow.api.IntegrationTest;
import pl.tokstudiow.api.user.Role;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.hasSize;

class AcademicTermTest extends IntegrationTest {

    private String adminToken;
    private String studentToken;

    @BeforeEach
    void setUp() throws Exception {
        createUser("Ada", "Admin", "admin@test.pl", Role.ADMIN);
        createUser("Anna", "Nowak", "anna@test.pl", Role.STUDENT);
        adminToken = tokenFor("admin@test.pl");
        studentToken = tokenFor("anna@test.pl");
    }

    @Test
    void adminCreatesTermWhichIsNotCurrentByDefault() throws Exception {
        createTerm("2026/2027 zimowy", "WINTER", "2026-10-01", "2027-02-20")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.startDate").value("2026-10-01"))
                .andExpect(jsonPath("$.current").value(false));
    }

    @Test
    void studentCannotCreateTerm() throws Exception {
        mvc.perform(post("/api/admin/academic-terms")
                        .header(AUTHORIZATION, bearer(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(termJson("X", "WINTER", "2026-10-01", "2027-02-20")))
                .andExpect(status().isForbidden());
    }

    @Test
    void endDateBeforeStartDateIsRejected() throws Exception {
        createTerm("Zły", "WINTER", "2027-02-20", "2026-10-01").andExpect(status().isBadRequest());
    }

    @Test
    void overlappingTermIsRejected() throws Exception {
        createTerm("2026/2027 zimowy", "WINTER", "2026-10-01", "2027-02-20").andExpect(status().isCreated());

        createTerm("Nakładający się", "SUMMER", "2027-02-01", "2027-06-30").andExpect(status().isConflict());
    }

    @Test
    void noCurrentTermReturns404() throws Exception {
        mvc.perform(get("/api/academic-terms/current").header(AUTHORIZATION, bearer(studentToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void makingTermCurrentSwitchesFromPreviousOne() throws Exception {
        String winter = idOf(createTerm("2026/2027 zimowy", "WINTER", "2026-10-01", "2027-02-20"));
        String summer = idOf(createTerm("2026/2027 letni", "SUMMER", "2027-02-21", "2027-09-30"));

        makeCurrent(winter).andExpect(status().isOk()).andExpect(jsonPath("$.current").value(true));
        makeCurrent(summer).andExpect(status().isOk());

        mvc.perform(get("/api/academic-terms/current").header(AUTHORIZATION, bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(summer));

        mvc.perform(get("/api/academic-terms").header(AUTHORIZATION, bearer(studentToken)))
                .andExpect(jsonPath("$[?(@.current == true)]", hasSize(1)));

        auditHistory("academic_term", winter, adminToken)
                .andExpect(jsonPath("$[0].action").value("UPDATE"))
                .andExpect(jsonPath("$[0].oldValue.current").value(true))
                .andExpect(jsonPath("$[0].newValue.current").value(false));
    }

    // pomocnicze

    private ResultActions createTerm(String name, String type, String start, String end) throws Exception {
        return mvc.perform(post("/api/admin/academic-terms")
                .header(AUTHORIZATION, bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(termJson(name, type, start, end)));
    }

    private ResultActions makeCurrent(String id) throws Exception {
        return mvc.perform(post("/api/admin/academic-terms/{id}/make-current", id)
                .header(AUTHORIZATION, bearer(adminToken)));
    }

    private static String termJson(String name, String type, String start, String end) {
        return """
                {"name": "%s", "type": "%s", "startDate": "%s", "endDate": "%s"}
                """.formatted(name, type, start, end);
    }
}