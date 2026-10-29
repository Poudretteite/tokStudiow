package pl.tokstudiow.api.auth;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import pl.tokstudiow.api.IntegrationTest;
import pl.tokstudiow.api.user.Role;
import pl.tokstudiow.api.user.User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RefreshTokenFlowTest extends IntegrationTest {

    private static final String EMAIL = "anna@test.pl";

    @BeforeEach
    void setUp() {
        createUser("Anna", "Nowak", EMAIL, Role.STUDENT);
    }

    @Test
    void loginReturnsRefreshToken() throws Exception {
        login(EMAIL, PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());
    }

    @Test
    void refreshReturnsNewWorkingTokens() throws Exception {
        Tokens first = tokens(login(EMAIL, PASSWORD));
        Tokens second = tokens(refresh(first.refresh()).andExpect(status().isOk()));

        assertThat(second.refresh()).isNotEqualTo(first.refresh());
        mvc.perform(get("/api/auth/me").header(AUTHORIZATION, bearer(second.access())))
                .andExpect(status().isOk());
    }

    @Test
    void usedRefreshTokenCannotBeUsedAgain() throws Exception {
        Tokens first = tokens(login(EMAIL, PASSWORD));
        refresh(first.refresh()).andExpect(status().isOk());

        refresh(first.refresh()).andExpect(status().isUnauthorized());
    }

    @Test
    void reuseOfOldTokenRevokesAllSessions() throws Exception {
        Tokens first = tokens(login(EMAIL, PASSWORD));
        Tokens second = tokens(refresh(first.refresh()));

        // napastnik używa skradzionego, już wymienionego tokenu
        refresh(first.refresh()).andExpect(status().isUnauthorized());

        // przez to również najnowszy token prawowitego użytkownika przestaje działać
        refresh(second.refresh()).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesRefreshToken() throws Exception {
        Tokens tokens = tokens(login(EMAIL, PASSWORD));

        mvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(tokens.refresh())))
                .andExpect(status().isNoContent());

        refresh(tokens.refresh()).andExpect(status().isUnauthorized());
    }

    @Test
    void unknownRefreshTokenIsRejected() throws Exception {
        refresh("not-a-real-token")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid refresh token"));
    }

    @Test
    void deactivatedUserCannotRefresh() throws Exception {
        Tokens tokens = tokens(login(EMAIL, PASSWORD));
        User anna = users.findByEmailIgnoreCase(EMAIL).orElseThrow();
        anna.deactivate();
        users.save(anna);

        refresh(tokens.refresh()).andExpect(status().isUnauthorized());
    }

    // pomocnicze

    private record Tokens(String access, String refresh) {
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(refreshBody(refreshToken)));
    }

    private static String refreshBody(String refreshToken) {
        return """
                {"refreshToken": "%s"}
                """.formatted(refreshToken);
    }

    private static Tokens tokens(ResultActions result) throws Exception {
        String json = result.andReturn().getResponse().getContentAsString();
        return new Tokens(JsonPath.read(json, "$.accessToken"), JsonPath.read(json, "$.refreshToken"));
    }
}
