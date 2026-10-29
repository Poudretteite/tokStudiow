package pl.tokstudiow.api.admin;

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
import pl.tokstudiow.api.TestcontainersConfiguration;
import pl.tokstudiow.api.user.Role;
import pl.tokstudiow.api.user.User;
import pl.tokstudiow.api.user.UserRepository;
import pl.tokstudiow.api.DatabaseCleaner;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AdminUserControllerTest {

    private static final String PASSWORD = "Correct-Horse-Battery-1";

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired DatabaseCleaner databaseCleaner;

    private User admin;

    @BeforeEach
    void setUp() {
        databaseCleaner.clean();
        admin = users.save(new User("Ada", "Admin", "admin@test.pl", passwordEncoder.encode(PASSWORD), Role.ADMIN));
        users.save(new User("Anna", "Nowak", "anna@test.pl", passwordEncoder.encode(PASSWORD), Role.STUDENT));
    }

    // kontrola dostępu

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
    }

    @Test
    void studentCannotAccessAdminEndpoints() throws Exception {
        String token = tokenFor("anna@test.pl");

        mvc.perform(get("/api/admin/users").header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden());
        createUser(token, newUserJson("eve@test.pl", "ADMIN", PASSWORD))
                .andExpect(status().isForbidden());
    }

    // tworzenie kont

    @Test
    void adminCanCreateUserWhoCanThenLogIn() throws Exception {
        createUser(tokenFor("admin@test.pl"), newUserJson("jan@test.pl", "LECTURER", PASSWORD))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.role").value("LECTURER"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());

        login("jan@test.pl", PASSWORD).andExpect(status().isOk());
    }

    @Test
    void duplicateEmailIsRejectedRegardlessOfCase() throws Exception {
        createUser(tokenFor("admin@test.pl"), newUserJson("ANNA@test.pl", "STUDENT", PASSWORD))
                .andExpect(status().isConflict());
    }

    @Test
    void shortPasswordIsRejected() throws Exception {
        createUser(tokenFor("admin@test.pl"), newUserJson("jan@test.pl", "STUDENT", "short"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownRoleIsRejected() throws Exception {
        createUser(tokenFor("admin@test.pl"), newUserJson("jan@test.pl", "SUPERADMIN", PASSWORD))
                .andExpect(status().isBadRequest());
    }

    // dezaktywacja

    @Test
    void deactivatedUserLosesAccessImmediately() throws Exception {
        String annaRefresh = JsonPath.read(
                login("anna@test.pl", PASSWORD).andReturn().getResponse().getContentAsString(),
                "$.refreshToken");
        String annaId = users.findByEmailIgnoreCase("anna@test.pl").orElseThrow().getId().toString();

        mvc.perform(post("/api/admin/users/{id}/deactivate", annaId)
                        .header(AUTHORIZATION, bearer(tokenFor("admin@test.pl"))))
                .andExpect(status().isNoContent());

        login("anna@test.pl", PASSWORD).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\": \"" + annaRefresh + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminCannotDeactivateOwnAccount() throws Exception {
        mvc.perform(post("/api/admin/users/{id}/deactivate", admin.getId())
                        .header(AUTHORIZATION, bearer(tokenFor("admin@test.pl"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deactivatingUnknownUserReturns404() throws Exception {
        mvc.perform(post("/api/admin/users/{id}/deactivate", "00000000-0000-0000-0000-000000000000")
                        .header(AUTHORIZATION, bearer(tokenFor("admin@test.pl"))))
                .andExpect(status().isNotFound());
    }

    // pomocnicze

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s"}
                        """.formatted(email, password)));
    }

    private String tokenFor(String email) throws Exception {
        String json = login(email, PASSWORD).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.accessToken");
    }

    private ResultActions createUser(String token, String body) throws Exception {
        return mvc.perform(post("/api/admin/users")
                .header(AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static String newUserJson(String email, String role, String password) {
        return """
                {"firstName": "Jan", "lastName": "Kowalski", "email": "%s", "role": "%s", "password": "%s"}
                """.formatted(email, role, password);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}