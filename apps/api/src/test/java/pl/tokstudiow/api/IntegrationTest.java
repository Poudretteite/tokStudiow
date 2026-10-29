package pl.tokstudiow.api;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import pl.tokstudiow.api.user.Role;
import pl.tokstudiow.api.user.User;
import pl.tokstudiow.api.user.UserRepository;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Baza testów integracyjnych: prawdziwy PostgreSQL w kontenerze,
 * czysta baza przed każdym testem i wspólne metody pomocnicze.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTest {

    protected static final String PASSWORD = "Correct-Horse-Battery-1";

    @Autowired protected MockMvc mvc;
    @Autowired protected UserRepository users;
    @Autowired protected PasswordEncoder passwordEncoder;
    @Autowired protected JdbcClient jdbc;
    @Autowired private DatabaseCleaner databaseCleaner;

    @BeforeEach
    void cleanDatabase() {
        databaseCleaner.clean();
    }

    // użytkownicy i logowanie

    protected User createUser(String firstName, String lastName, String email, Role role) {
        return users.save(new User(firstName, lastName, email, passwordEncoder.encode(PASSWORD), role));
    }

    protected ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s"}
                        """.formatted(email, password)));
    }

    protected String tokenFor(String email) throws Exception {
        String json = login(email, PASSWORD).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.accessToken");
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }

    // odpowiedzi i audyt

    protected static String idOf(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
    }

    protected ResultActions auditHistory(String entityType, String entityId, String token) throws Exception {
        return mvc.perform(get("/api/admin/audit-log")
                .param("entityType", entityType)
                .param("entityId", entityId)
                .header(AUTHORIZATION, bearer(token)));
    }
}
