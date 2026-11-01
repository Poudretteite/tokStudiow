package pl.tokstudiow.api.people;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import pl.tokstudiow.api.IntegrationTest;
import pl.tokstudiow.api.university.Faculty;
import pl.tokstudiow.api.university.FacultyRepository;
import pl.tokstudiow.api.user.Role;

import java.util.UUID;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProfileTest extends IntegrationTest {

    @Autowired FacultyRepository faculties;

    private String adminToken;
    private String facultyId;

    @BeforeEach
    void setUp() throws Exception {
        createUser("Ada", "Admin", "admin@test.pl", Role.ADMIN);
        adminToken = tokenFor("admin@test.pl");
        facultyId = faculties.save(new Faculty("Wydział Nauk Ścisłych i Technicznych", "WNŚiT")).getId().toString();
    }

    // student

    @Test
    void studentRequiresAlbumNumber() throws Exception {
        createUser("""
                "email": "anna@test.pl", "role": "STUDENT"
                """).andExpect(status().isBadRequest());
    }

    @Test
    void studentSeesOwnAlbumNumber() throws Exception {
        createUser("""
                "email": "anna@test.pl", "role": "STUDENT", "albumNumber": "123456"
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.albumNumber").value("123456"));

        mvc.perform(get("/api/me").header(AUTHORIZATION, bearer(tokenFor("anna@test.pl"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.albumNumber").value("123456"));
    }

    @Test
    void duplicateAlbumNumberIsRejected() throws Exception {
        createUser("""
                "email": "anna@test.pl", "role": "STUDENT", "albumNumber": "123456"
                """).andExpect(status().isCreated());

        createUser("""
                "email": "jan@test.pl", "role": "STUDENT", "albumNumber": "123456"
                """).andExpect(status().isConflict());
    }

    @Test
    void albumNumberWithForbiddenCharactersIsRejected() throws Exception {
        createUser("""
                "email": "anna@test.pl", "role": "STUDENT", "albumNumber": "12<script>"
                """).andExpect(status().isBadRequest());
    }

    // prowadzący i dziekanat

    @Test
    void lecturerRequiresFaculty() throws Exception {
        createUser("""
                "email": "jan@test.pl", "role": "LECTURER"
                """).andExpect(status().isBadRequest());
    }

    @Test
    void lecturerWithUnknownFacultyReturns404() throws Exception {
        createUser("""
                "email": "jan@test.pl", "role": "LECTURER", "facultyId": "%s"
                """.formatted(UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    void lecturerSeesOwnFacultyAndTitle() throws Exception {
        createUser("""
                "email": "jan@test.pl", "role": "LECTURER", "facultyId": "%s", "academicTitle": "dr inż."
                """.formatted(facultyId)).andExpect(status().isCreated());

        mvc.perform(get("/api/me").header(AUTHORIZATION, bearer(tokenFor("jan@test.pl"))))
                .andExpect(jsonPath("$.facultyId").value(facultyId))
                .andExpect(jsonPath("$.academicTitle").value("dr inż."));
    }

    @Test
    void deanOfficeEmployeeIsAssignedToFaculty() throws Exception {
        createUser("""
                "email": "ewa@test.pl", "role": "DEAN_OFFICE", "facultyId": "%s"
                """.formatted(facultyId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.facultyId").value(facultyId));
    }

    @Test
    void failedProfileValidationCreatesNoAccount() throws Exception {
        createUser("""
                "email": "jan@test.pl", "role": "LECTURER"
                """).andExpect(status().isBadRequest());

        login("jan@test.pl", PASSWORD).andExpect(status().isUnauthorized());
    }

    // pomocnicze

    /** Tworzy konto przez API; `fields` to pola JSON specyficzne dla testu. */
    private ResultActions createUser(String fields) throws Exception {
        return mvc.perform(post("/api/admin/users")
                .header(AUTHORIZATION, bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"firstName": "Jan", "lastName": "Kowalski", "password": "%s", %s}
                        """.formatted(PASSWORD, fields.strip())));
    }
}