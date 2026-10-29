package pl.tokstudiow.api.university;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import pl.tokstudiow.api.DatabaseCleaner;
import pl.tokstudiow.api.TestcontainersConfiguration;
import pl.tokstudiow.api.user.Role;
import pl.tokstudiow.api.user.User;
import pl.tokstudiow.api.user.UserRepository;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class FacultyAndFieldOfStudyTest {

    private static final String PASSWORD = "Correct-Horse-Battery-1";

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired DatabaseCleaner databaseCleaner;

    private String adminToken;
    private String studentToken;

    @BeforeEach
    void setUp() throws Exception {
        databaseCleaner.clean();
        users.save(new User("Ada", "Admin", "admin@test.pl", passwordEncoder.encode(PASSWORD), Role.ADMIN));
        users.save(new User("Anna", "Nowak", "anna@test.pl", passwordEncoder.encode(PASSWORD), Role.STUDENT));
        adminToken = tokenFor("admin@test.pl");
        studentToken = tokenFor("anna@test.pl");
    }

    // wydziały

    @Test
    void anyAuthenticatedUserCanListFaculties() throws Exception {
        createFaculty("Wydział Nauk Ścisłych i Technicznych", "WNŚiT").andExpect(status().isCreated());

        mvc.perform(get("/api/faculties").header(AUTHORIZATION, bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].shortName").value("WNŚiT"));
    }

    @Test
    void unauthenticatedUserCannotListFaculties() throws Exception {
        mvc.perform(get("/api/faculties")).andExpect(status().isUnauthorized());
    }

    @Test
    void studentCannotCreateFaculty() throws Exception {
        mvc.perform(post("/api/admin/faculties")
                        .header(AUTHORIZATION, bearer(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(facultyJson("Wydział X", "WX")))
                .andExpect(status().isForbidden());
    }

    @Test
    void creatingFacultyIsAudited() throws Exception {
        String id = idOf(createFaculty("Wydział Medyczny", "WM").andExpect(status().isCreated()));

        auditHistory("faculty", id)
                .andExpect(jsonPath("$[0].action").value("CREATE"))
                .andExpect(jsonPath("$[0].newValue.shortName").value("WM"));
    }

    @Test
    void duplicateShortNameIsRejectedRegardlessOfCase() throws Exception {
        createFaculty("Wydział Medyczny", "WM").andExpect(status().isCreated());

        createFaculty("Inny Wydział", "wm").andExpect(status().isConflict());
    }

    @Test
    void updatingFacultyRecordsOldAndNewValues() throws Exception {
        String id = idOf(createFaculty("Wydział Medyczny", "WM"));

        mvc.perform(put("/api/admin/faculties/{id}", id)
                        .header(AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(facultyJson("Kolegium Nauk Medycznych", "KNM")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Kolegium Nauk Medycznych"));

        auditHistory("faculty", id)
                .andExpect(jsonPath("$[0].action").value("UPDATE"))
                .andExpect(jsonPath("$[0].oldValue.name").value("Wydział Medyczny"))
                .andExpect(jsonPath("$[0].newValue.name").value("Kolegium Nauk Medycznych"));
    }

    // kierunki

    @Test
    void adminCreatesFieldOfStudyVisibleToStudents() throws Exception {
        String facultyId = idOf(createFaculty("Wydział Nauk Ścisłych i Technicznych", "WNŚiT"));

        createField(facultyId, "Informatyka", "FIRST_CYCLE", "FULL_TIME", 7)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.facultyId").value(facultyId))
                .andExpect(jsonPath("$.semestersCount").value(7));

        mvc.perform(get("/api/faculties/{id}/fields-of-study", facultyId).header(AUTHORIZATION, bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Informatyka"));
    }

    @Test
    void sameNameIsAllowedForDifferentLevelButNotTwice() throws Exception {
        String facultyId = idOf(createFaculty("Wydział Nauk Ścisłych i Technicznych", "WNŚiT"));

        createField(facultyId, "Informatyka", "FIRST_CYCLE", "FULL_TIME", 7).andExpect(status().isCreated());
        createField(facultyId, "Informatyka", "SECOND_CYCLE", "FULL_TIME", 3).andExpect(status().isCreated());
        createField(facultyId, "informatyka", "FIRST_CYCLE", "FULL_TIME", 7).andExpect(status().isConflict());
    }

    @Test
    void invalidSemestersCountIsRejected() throws Exception {
        String facultyId = idOf(createFaculty("Wydział Medyczny", "WM"));

        createField(facultyId, "Lekarski", "FIRST_CYCLE", "FULL_TIME", 0).andExpect(status().isBadRequest());
    }

    @Test
    void fieldOfStudyInUnknownFacultyReturns404() throws Exception {
        createField("00000000-0000-0000-0000-000000000000", "Informatyka", "FIRST_CYCLE", "FULL_TIME", 7)
                .andExpect(status().isNotFound());
    }

    // pomocnicze

    private ResultActions createFaculty(String name, String shortName) throws Exception {
        return mvc.perform(post("/api/admin/faculties")
                .header(AUTHORIZATION, bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(facultyJson(name, shortName)));
    }

    private ResultActions createField(String facultyId, String name, String level, String mode, int semesters)
            throws Exception {
        return mvc.perform(post("/api/admin/fields-of-study")
                .header(AUTHORIZATION, bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"facultyId": "%s", "name": "%s", "level": "%s", "mode": "%s", "semestersCount": %d}
                        """.formatted(facultyId, name, level, mode, semesters)));
    }

    private ResultActions auditHistory(String entityType, String entityId) throws Exception {
        return mvc.perform(get("/api/admin/audit-log")
                .param("entityType", entityType)
                .param("entityId", entityId)
                .header(AUTHORIZATION, bearer(adminToken)));
    }

    private static String facultyJson(String name, String shortName) {
        return """
                {"name": "%s", "shortName": "%s"}
                """.formatted(name, shortName);
    }

    private static String idOf(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
    }

    private String tokenFor(String email) throws Exception {
        String json = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s"}
                                """.formatted(email, PASSWORD)))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.accessToken");
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}