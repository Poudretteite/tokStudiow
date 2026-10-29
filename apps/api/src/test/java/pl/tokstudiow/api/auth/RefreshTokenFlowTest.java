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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RefreshTokenFlowTest {

    private static final String EMAIL = "anna@test.pl";
    private static final String PASSWORD = "Correct-Horse-Battery-1";

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired DatabaseCleaner databaseCleaner;

    @BeforeEach
    void setUp() {
        databaseCleaner.clean();
        users.save(new User("Anna", "Nowak", EMAIL, passwordEncoder.encode(PASSWORD), Role.STUDENT));
    }

    @Test
    void loginReturnsRefreshToken() throws Exception {
        login().andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());
    }

    @Test
    void refreshReturnsNewWorkingTokens() throws Exception {
        Tokens first = tokens(login());
        Tokens second = tokens(refresh(first.refresh()).andExpect(status().isOk()));

        assertThat(second.refresh()).isNotEqualTo(first.refresh());
        mvc.perform(get("/api/auth/me").header(AUTHORIZATION, "Bearer " + second.access()))
                .andExpect(status().isOk());
    }

    @Test
    void usedRefreshTokenCannotBeUsedAgain() throws Exception {
        Tokens first = tokens(login());
        refresh(first.refresh()).andExpect(status().isOk());

        refresh(first.refresh()).andExpect(status().isUnauthorized());
    }

    @Test
    void reuseOfOldTokenRevokesAllSessions() throws Exception {
        Tokens first = tokens(login());
        Tokens second = tokens(refresh(first.refresh()));

        // napastnik używa skradzionego, już wymienionego tokenu
        refresh(first.refresh()).andExpect(status().isUnauthorized());

        // przez to również najnowszy token prawowitego użytkownika przestaje działać
        refresh(second.refresh()).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesRefreshToken() throws Exception {
        Tokens tokens = tokens(login());

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
        Tokens tokens = tokens(login());
        User anna = users.findByEmailIgnoreCase(EMAIL).orElseThrow();
        anna.deactivate();
        users.save(anna);

        refresh(tokens.refresh()).andExpect(status().isUnauthorized());
    }

    // ---------- pomocnicze ----------

    private record Tokens(String access, String refresh) {
    }

    private ResultActions login() throws Exception {
        String body = """
                {"email": "%s", "password": "%s"}
                """.formatted(EMAIL, PASSWORD);
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body));
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