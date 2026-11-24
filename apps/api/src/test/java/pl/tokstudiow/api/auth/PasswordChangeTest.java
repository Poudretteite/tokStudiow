package pl.tokstudiow.api.auth;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import pl.tokstudiow.api.IntegrationTest;
import pl.tokstudiow.api.user.Role;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpHeaders.SET_COOKIE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Zmiana własnego hasła i blokada konta z hasłem startowym. */
class PasswordChangeTest extends IntegrationTest {

    private static final String NEW_PASSWORD = "zielony-rower-nad-Wisłą";

    private UUID annaId;

    @BeforeEach
    void setUp() {
        annaId = createUser("Anna", "Nowak", "anna@test.pl", Role.STUDENT).getId();
        createUser("Ada", "Admin", "admin@test.pl", Role.ADMIN);
    }

    // hasło startowe

    @Test
    void accountCreatedByAdminMustChangePassword() throws Exception {
        mvc.perform(post("/api/admin/users")
                        .header(AUTHORIZATION, bearer(tokenFor("admin@test.pl")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName": "Jan", "lastName": "Kowalski", "email": "jan@test.pl",
                                 "role": "ADMIN", "password": "%s"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mustChangePassword").value(true));

        mvc.perform(get("/api/me").header(AUTHORIZATION, bearer(tokenFor("jan@test.pl"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(true));
    }

    @Test
    void accountWithStartPasswordCanOnlyChangePasswordAndReadOwnProfile() throws Exception {
        requirePasswordChange("anna@test.pl");
        String token = tokenFor("anna@test.pl");

        mvc.perform(get("/api/me").header(AUTHORIZATION, bearer(token))).andExpect(status().isOk());
        mvc.perform(get("/api/me/studies").header(AUTHORIZATION, bearer(token))).andExpect(status().isForbidden());
        mvc.perform(get("/api/me/groups").header(AUTHORIZATION, bearer(token))).andExpect(status().isForbidden());
    }

    @Test
    void adminWithStartPasswordLosesAdminRightsUntilChange() throws Exception {
        requirePasswordChange("admin@test.pl");

        mvc.perform(get("/api/admin/users").header(AUTHORIZATION, bearer(tokenFor("admin@test.pl"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void refreshingTokenDoesNotLiftTheRestriction() throws Exception {
        requirePasswordChange("anna@test.pl");
        String refreshToken = JsonPath.read(login("anna@test.pl", PASSWORD).andReturn().getResponse()
                .getContentAsString(), "$.refreshToken");

        String refreshed = JsonPath.read(mvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\": \"" + refreshToken + "\"}"))
                .andReturn().getResponse().getContentAsString(), "$.accessToken");

        mvc.perform(get("/api/me/studies").header(AUTHORIZATION, bearer(refreshed))).andExpect(status().isForbidden());
    }

    @Test
    void changingStartPasswordUnlocksAccount() throws Exception {
        requirePasswordChange("anna@test.pl");

        String json = changePassword(tokenFor("anna@test.pl"), PASSWORD, NEW_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String newToken = JsonPath.read(json, "$.accessToken");

        mvc.perform(get("/api/me/studies").header(AUTHORIZATION, bearer(newToken))).andExpect(status().isOk());
        mvc.perform(get("/api/me").header(AUTHORIZATION, bearer(newToken)))
                .andExpect(jsonPath("$.mustChangePassword").value(false));
        login("anna@test.pl", PASSWORD).andExpect(status().isUnauthorized());
        login("anna@test.pl", NEW_PASSWORD).andExpect(status().isOk());
    }

    // zmiana hasła

    @Test
    void changeEndsOtherSessions() throws Exception {
        String otherDevice = JsonPath.read(login("anna@test.pl", PASSWORD).andReturn().getResponse()
                .getContentAsString(), "$.refreshToken");

        changePassword(tokenFor("anna@test.pl"), PASSWORD, NEW_PASSWORD).andExpect(status().isOk());

        mvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\": \"" + otherDevice + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void browserGetsNewRefreshTokenOnlyInHttpOnlyCookie() throws Exception {
        mvc.perform(post("/api/auth/password")
                        .header(AUTHORIZATION, bearer(tokenFor("anna@test.pl")))
                        .header("X-Client", "web")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(header().string(SET_COOKIE, containsString("HttpOnly")));
    }

    @Test
    void wrongCurrentPasswordIsRejected() throws Exception {
        changePassword(tokenFor("anna@test.pl"), "Zle-haslo-1234", NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Current password is incorrect"));
        login("anna@test.pl", PASSWORD).andExpect(status().isOk());
    }

    @Test
    void weakOrSamePasswordIsRejected() throws Exception {
        String token = tokenFor("anna@test.pl");

        changePassword(token, PASSWORD, "krotkie-1").andExpect(jsonPath("$.detail")
                .value("Password must have at least 12 characters"));
        changePassword(token, PASSWORD, "Anna-Nowak-2026!").andExpect(jsonPath("$.detail")
                .value("Password must not contain your name or login"));
        changePassword(token, PASSWORD, "Tokstudiow-2026!").andExpect(jsonPath("$.detail")
                .value("Password is too easy to guess"));
        changePassword(token, PASSWORD, PASSWORD).andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail")
                .value("New password must differ from the current one"));
    }

    @Test
    void changeIsAuditedWithoutPassword() throws Exception {
        changePassword(tokenFor("anna@test.pl"), PASSWORD, NEW_PASSWORD).andExpect(status().isOk());

        auditHistory("user", annaId.toString(), tokenFor("admin@test.pl"))
                .andExpect(jsonPath("$[0].newValue.event").value("PASSWORD_CHANGED"))
                .andExpect(jsonPath("$[0].newValue.mustChangePassword").value(false));
        String raw = jdbc.sql("SELECT CAST(new_value AS text) FROM audit_log WHERE entity_id = :id")
                .param("id", annaId).query(String.class).single();
        assertThat(raw).doesNotContain(NEW_PASSWORD).doesNotContain("$2a$");
    }

    @Test
    void anonymousCannotChangePassword() throws Exception {
        mvc.perform(post("/api/auth/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    // pomocnicze

    private void requirePasswordChange(String email) {
        jdbc.sql("UPDATE users SET must_change_password = TRUE WHERE email = :email").param("email", email).update();
    }

    private ResultActions changePassword(String token, String current, String next) throws Exception {
        return mvc.perform(post("/api/auth/password")
                .header(AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(current, next)));
    }

    private static String body(String current, String next) {
        return """
                {"currentPassword": "%s", "newPassword": "%s"}
                """.formatted(current, next);
    }
}
