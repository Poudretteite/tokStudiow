package pl.tokstudiow.api.auth;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import pl.tokstudiow.api.IntegrationTest;
import pl.tokstudiow.api.user.Role;
import pl.tokstudiow.api.user.User;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpHeaders.RETRY_AFTER;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Ochrona przed zgadywaniem haseł: 5 prób na konto i 20 na adres IP w ciągu 15 minut. */
class LoginThrottleTest extends IntegrationTest {

    private static final String WRONG = "Zle-haslo-1234";

    private User anna;

    @BeforeEach
    void setUp() {
        anna = createUser("Anna", "Nowak", "anna@test.pl", Role.STUDENT);
        createUser("Jan", "Kowalski", "jan@test.pl", Role.STUDENT);
        createUser("Ada", "Admin", "admin@test.pl", Role.ADMIN);
    }

    // konto

    @Test
    void accountIsLockedAfterFiveFailuresEvenForCorrectPassword() throws Exception {
        failLogins("anna@test.pl", 5);

        loginFrom("anna@test.pl", PASSWORD, "127.0.0.1")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.detail").value("Too many attempts"))
                .andExpect(header().string(RETRY_AFTER, String.valueOf(15 * 60)));
    }

    @Test
    void unknownEmailIsLockedTheSameWaySoAccountsCannotBeDiscovered() throws Exception {
        failLogins("nikt@test.pl", 5);

        loginFrom("nikt@test.pl", WRONG, "127.0.0.1").andExpect(status().isTooManyRequests());
    }

    /** Wielkość liter nie daje nowego licznika; adres ze spacjami odrzuca już walidacja (400). */
    @Test
    void lockIgnoresLetterCaseInEmail() throws Exception {
        failLogins("anna@test.pl", 4);
        loginFrom("ANNA@Test.PL", WRONG, "127.0.0.1").andExpect(status().isUnauthorized());

        loginFrom("anna@test.pl", PASSWORD, "127.0.0.1").andExpect(status().isTooManyRequests());
    }

    @Test
    void successfulLoginResetsAccountCounter() throws Exception {
        failLogins("anna@test.pl", 4);
        login("anna@test.pl", PASSWORD).andExpect(status().isOk());
        failLogins("anna@test.pl", 4);

        login("anna@test.pl", PASSWORD).andExpect(status().isOk());
    }

    @Test
    void lockEndsAfterLockDuration() throws Exception {
        failLogins("anna@test.pl", 5);
        // przesunięcie w czasie: blokada i okno liczenia minęły
        jdbc.sql("""
                UPDATE login_throttle
                SET locked_until = now() - interval '1 second', window_started_at = now() - interval '1 hour'
                """).update();

        login("anna@test.pl", PASSWORD).andExpect(status().isOk());
    }

    @Test
    void lockOfOneAccountDoesNotAffectOthers() throws Exception {
        failLogins("anna@test.pl", 5);

        login("jan@test.pl", PASSWORD).andExpect(status().isOk());
    }

    @Test
    void lockIsRecordedInAuditLog() throws Exception {
        failLogins("anna@test.pl", 5);

        auditHistory("user", anna.getId().toString(), tokenFor("admin@test.pl"))
                .andExpect(jsonPath("$[0].newValue.event").value("LOGIN_LOCKED"))
                .andExpect(jsonPath("$[0].actorId").doesNotExist());
    }

    @Test
    void resetByAdminUnlocksAccount() throws Exception {
        failLogins("anna@test.pl", 5);

        String json = mvc.perform(post("/api/admin/users/{id}/reset-password", anna.getId())
                        .header(AUTHORIZATION, bearer(tokenFor("admin@test.pl"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        login("anna@test.pl", JsonPath.read(json, "$.initialPassword")).andExpect(status().isOk());
    }

    // adres IP

    @Test
    void ipIsLockedAfterTwentyFailuresAcrossAccountsAndForwardedHeaderDoesNotHelp() throws Exception {
        for (int i = 0; i < 20; i++) {
            loginFrom("osoba" + i + "@test.pl", WRONG, "10.0.0.5").andExpect(status().isUnauthorized());
        }

        loginFrom("jan@test.pl", PASSWORD, "10.0.0.5").andExpect(status().isTooManyRequests());
        // nagłówek ustawiany przez klienta nie zmienia adresu, z którego liczone są próby
        mvc.perform(post("/api/auth/login")
                        .with(request -> {
                            request.setRemoteAddr("10.0.0.5");
                            return request;
                        })
                        .header("X-Forwarded-For", "203.0.113.7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("jan@test.pl", PASSWORD)))
                .andExpect(status().isTooManyRequests());
        // inny adres IP loguje się normalnie
        loginFrom("jan@test.pl", PASSWORD, "10.0.0.6").andExpect(status().isOk());
    }

    // zmiana hasła

    @Test
    void guessingCurrentPasswordWithStolenTokenIsLimited() throws Exception {
        String token = tokenFor("anna@test.pl");
        for (int i = 0; i < 5; i++) {
            changePassword(token, WRONG).andExpect(status().isBadRequest());
        }

        changePassword(token, PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(RETRY_AFTER, String.valueOf(15 * 60)));
    }

    // dane w bazie

    @Test
    void countersStoreNoEmailOrIpInPlainText() throws Exception {
        loginFrom("anna@test.pl", WRONG, "10.0.0.5").andExpect(status().isUnauthorized());

        List<String> keys = jdbc.sql("SELECT key_hash FROM login_throttle").query(String.class).list();
        assertThat(keys).hasSize(2).allSatisfy(key -> assertThat(key).hasSize(64).doesNotContain("anna", "10.0.0.5"));
    }

    // pomocnicze

    private void failLogins(String email, int times) throws Exception {
        for (int i = 0; i < times; i++) {
            loginFrom(email, WRONG, "127.0.0.1").andExpect(status().isUnauthorized());
        }
    }

    private ResultActions loginFrom(String email, String password, String ip) throws Exception {
        return mvc.perform(post("/api/auth/login")
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(credentials(email, password)));
    }

    private ResultActions changePassword(String token, String current) throws Exception {
        return mvc.perform(post("/api/auth/password")
                .header(AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"currentPassword": "%s", "newPassword": "zielony-rower-nad-Wisłą"}
                        """.formatted(current)));
    }

    private static String credentials(String email, String password) {
        return """
                {"email": "%s", "password": "%s"}
                """.formatted(email, password);
    }
}
