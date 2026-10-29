package pl.tokstudiow.api.auth;

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

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthControllerTest {

    private static final String PASSWORD = "Correct-Horse-Battery-1";
    private static final String GENERIC_ERROR = "Invalid email or password";

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired DatabaseCleaner databaseCleaner;

    @BeforeEach
    void setUp() {
        databaseCleaner.clean();
        users.save(new User("Anna", "Nowak", "anna@test.pl",
                passwordEncoder.encode(PASSWORD), Role.STUDENT));

        User inactive = new User("Jan", "Kowalski", "jan@test.pl",
                passwordEncoder.encode(PASSWORD), Role.STUDENT);
        inactive.deactivate();
        users.save(inactive);
    }

    // logowanie

    @Test
    void validCredentialsReturnAccessToken() throws Exception {
        login("anna@test.pl", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900));
    }

    @Test
    void emailIsCaseInsensitive() throws Exception {
        login("ANNA@Test.pl", PASSWORD).andExpect(status().isOk());
    }

    @Test
    void wrongPasswordReturnsGeneric401() throws Exception {
        login("anna@test.pl", "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value(GENERIC_ERROR));
    }

    @Test
    void unknownEmailReturnsSameResponseAsWrongPassword() throws Exception {
        login("nobody@test.pl", PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value(GENERIC_ERROR));
    }

    @Test
    void inactiveAccountCannotLogIn() throws Exception {
        login("jan@test.pl", PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value(GENERIC_ERROR));
    }

    @Test
    void invalidRequestBodyReturns400() throws Exception {
        login("not-an-email", "").andExpect(status().isBadRequest());
    }

    // dostęp z tokenem

    @Test
    void protectedEndpointWithoutTokenReturns401() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void validTokenGivesAccessWithCorrectIdentity() throws Exception {
        String token = obtainToken("anna@test.pl");
        String annaId = users.findByEmailIgnoreCase("anna@test.pl").orElseThrow().getId().toString();

        mvc.perform(get("/api/auth/me").header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(annaId))
                .andExpect(jsonPath("$.role").value("STUDENT"));
    }

    @Test
    void tokenWithForgedRoleIsRejected() throws Exception {
        String token = obtainToken("anna@test.pl");
        String[] parts = token.split("\\.");

        // Napastnik podmienia rolę w treści tokenu, zostawiając oryginalny podpis
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        String forgedPayload = payload.replace("\"STUDENT\"", "\"ADMIN\"");
        String forgedToken = parts[0] + "."
                + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(forgedPayload.getBytes(StandardCharsets.UTF_8))
                + "." + parts[2];

        mvc.perform(get("/api/auth/me").header(AUTHORIZATION, "Bearer " + forgedToken))
                .andExpect(status().isUnauthorized());
    }

    // pomocnicze

    private ResultActions login(String email, String password) throws Exception {
        String body = """
                {"email": "%s", "password": "%s"}
                """.formatted(email, password);
        return mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String obtainToken(String email) throws Exception {
        String response = login(email, PASSWORD).andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.accessToken");
    }
}