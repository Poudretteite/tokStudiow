package pl.tokstudiow.api.auth;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.ResultActions;
import pl.tokstudiow.api.IntegrationTest;
import pl.tokstudiow.api.user.Role;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.HttpHeaders.SET_COOKIE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WebRefreshCookieTest extends IntegrationTest {

    @BeforeEach
    void setUp() {
        createUser("Anna", "Nowak", "anna@test.pl", Role.STUDENT);
    }

    @Test
    void webLoginSetsSecureHttpOnlyCookieAndHidesTokenFromBody() throws Exception {
        webLogin()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(header().string(SET_COOKIE, allOf(
                        containsString("refresh_token="),
                        containsString("HttpOnly"),
                        containsString("Secure"),
                        containsString("SameSite=Strict"),
                        containsString("Path=/api/auth"))));
    }

    @Test
    void refreshWithCookieIssuesNewCookie() throws Exception {
        Cookie first = refreshCookie(webLogin());

        MockHttpServletResponse response = mvc.perform(post("/api/auth/refresh")
                        .header("X-Client", "web")
                        .cookie(first))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn().getResponse();

        assertThat(response.getCookie("refresh_token").getValue()).isNotEqualTo(first.getValue());
    }

    @Test
    void cookieWithoutClientHeaderIsIgnored() throws Exception {
        Cookie cookie = refreshCookie(webLogin());

        // żądanie z obcej strony nie może dodać nagłówka X-Client
        mvc.perform(post("/api/auth/refresh").cookie(cookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void webLogoutRevokesTokenAndClearsCookie() throws Exception {
        Cookie cookie = refreshCookie(webLogin());

        mvc.perform(post("/api/auth/logout").header("X-Client", "web").cookie(cookie))
                .andExpect(status().isNoContent())
                .andExpect(header().string(SET_COOKIE, containsString("Max-Age=0")));

        mvc.perform(post("/api/auth/refresh").header("X-Client", "web").cookie(cookie))
                .andExpect(status().isUnauthorized());
    }

    // pomocnicze

    private ResultActions webLogin() throws Exception {
        return mvc.perform(post("/api/auth/login")
                .header("X-Client", "web")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "anna@test.pl", "password": "%s"}
                        """.formatted(PASSWORD)));
    }

    private static Cookie refreshCookie(ResultActions result) {
        return result.andReturn().getResponse().getCookie("refresh_token");
    }
}