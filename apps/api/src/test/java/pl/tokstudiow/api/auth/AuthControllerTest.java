package pl.tokstudiow.api.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.tokstudiow.api.IntegrationTest;
import pl.tokstudiow.api.user.Role;
import pl.tokstudiow.api.user.User;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest extends IntegrationTest {

    private static final String GENERIC_ERROR = "Invalid email or password";

    @BeforeEach
    void setUp() {
        createUser("Anna", "Nowak", "anna@test.pl", Role.STUDENT);
        User inactive = createUser("Jan", "Kowalski", "jan@test.pl", Role.STUDENT);
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
        String token = tokenFor("anna@test.pl");
        String annaId = users.findByEmailIgnoreCase("anna@test.pl").orElseThrow().getId().toString();

        mvc.perform(get("/api/auth/me").header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(annaId))
                .andExpect(jsonPath("$.role").value("STUDENT"));
    }

    @Test
    void tokenWithForgedRoleIsRejected() throws Exception {
        String token = tokenFor("anna@test.pl");
        String[] parts = token.split("\\.");

        // Napastnik podmienia rolę w treści tokenu, zostawiając oryginalny podpis
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        String forgedPayload = payload.replace("\"STUDENT\"", "\"ADMIN\"");
        String forgedToken = parts[0] + "."
                + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(forgedPayload.getBytes(StandardCharsets.UTF_8))
                + "." + parts[2];

        mvc.perform(get("/api/auth/me").header(AUTHORIZATION, bearer(forgedToken)))
                .andExpect(status().isUnauthorized());
    }
}
