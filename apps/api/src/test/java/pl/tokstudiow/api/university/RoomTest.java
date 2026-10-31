package pl.tokstudiow.api.university;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import pl.tokstudiow.api.IntegrationTest;
import pl.tokstudiow.api.user.Role;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RoomTest extends IntegrationTest {

    private String adminToken;
    private String studentToken;

    @BeforeEach
    void setUp() throws Exception {
        createUser("Ada", "Admin", "admin@test.pl", Role.ADMIN);
        createUser("Anna", "Nowak", "anna@test.pl", Role.STUDENT);
        adminToken = tokenFor("admin@test.pl");
        studentToken = tokenFor("anna@test.pl");
    }

    @Test
    void adminCreatesRoomVisibleToStudents() throws Exception {
        createRoom("A0", "101", "30").andExpect(status().isCreated());

        mvc.perform(get("/api/rooms").header(AUTHORIZATION, bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].capacity").value(30));
    }

    @Test
    void capacityIsOptional() throws Exception {
        createRoom("A0", "Aula", "null").andExpect(status().isCreated());
    }

    @Test
    void sameNumberInSameBuildingIsRejected() throws Exception {
        createRoom("A0", "101", "30").andExpect(status().isCreated());
        createRoom("B1", "101", "30").andExpect(status().isCreated());

        createRoom("a0", "101", "20").andExpect(status().isConflict());
    }

    @Test
    void nonPositiveCapacityIsRejected() throws Exception {
        createRoom("A0", "101", "0").andExpect(status().isBadRequest());
    }

    // pomocnicze

    private ResultActions createRoom(String building, String number, String capacity) throws Exception {
        return mvc.perform(post("/api/admin/rooms")
                .header(AUTHORIZATION, bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"building": "%s", "number": "%s", "capacity": %s}
                        """.formatted(building, number, capacity)));
    }
}