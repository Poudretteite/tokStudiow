package pl.tokstudiow.api.studies;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import pl.tokstudiow.api.IntegrationTest;
import pl.tokstudiow.api.people.DeanOfficeProfile;
import pl.tokstudiow.api.people.DeanOfficeProfileRepository;
import pl.tokstudiow.api.people.LecturerProfile;
import pl.tokstudiow.api.people.LecturerProfileRepository;
import pl.tokstudiow.api.people.StudentProfile;
import pl.tokstudiow.api.people.StudentProfileRepository;
import pl.tokstudiow.api.university.Faculty;
import pl.tokstudiow.api.university.FacultyRepository;
import pl.tokstudiow.api.university.FieldOfStudy;
import pl.tokstudiow.api.university.FieldOfStudyRepository;
import pl.tokstudiow.api.university.StudyLevel;
import pl.tokstudiow.api.university.StudyMode;
import pl.tokstudiow.api.user.Role;
import pl.tokstudiow.api.user.User;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StudyAccessTest extends IntegrationTest {

    @Autowired FacultyRepository faculties;
    @Autowired FieldOfStudyRepository fields;
    @Autowired StudentProfileRepository studentProfiles;
    @Autowired DeanOfficeProfileRepository deanOfficeProfiles;
    @Autowired LecturerProfileRepository lecturerProfiles;

    private String computerScienceId;   // kierunek na WNŚiT
    private String medicineId;          // kierunek na Wydziale Medycznym
    private String annaId;
    private String janId;

    private String annaToken;
    private String deanScienceToken;    // dziekanat WNŚiT
    private String deanMedicineToken;   // dziekanat Wydziału Medycznego
    private String lecturerToken;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        Faculty science = faculties.save(new Faculty("Wydział Nauk Ścisłych i Technicznych", "WNŚiT"));
        Faculty medicine = faculties.save(new Faculty("Wydział Medyczny", "WM"));
        computerScienceId = fields.save(new FieldOfStudy(
                science, "Informatyka", StudyLevel.FIRST_CYCLE, StudyMode.FULL_TIME, (short) 7)).getId().toString();
        medicineId = fields.save(new FieldOfStudy(
                medicine, "Kierunek lekarski", StudyLevel.FIRST_CYCLE, StudyMode.FULL_TIME, (short) 12)).getId().toString();

        annaId = createStudent("Anna", "Nowak", "anna@test.pl", "1001");
        janId = createStudent("Jan", "Kowalski", "jan@test.pl", "1002");

        User deanScience = createUser("Ewa", "Dziekanat", "dziekanat.wnsit@test.pl", Role.DEAN_OFFICE);
        deanOfficeProfiles.save(new DeanOfficeProfile(deanScience, science));
        User deanMedicine = createUser("Olga", "Dziekanat", "dziekanat.wm@test.pl", Role.DEAN_OFFICE);
        deanOfficeProfiles.save(new DeanOfficeProfile(deanMedicine, medicine));

        User lecturer = createUser("Piotr", "Wykładowca", "piotr@test.pl", Role.LECTURER);
        lecturerProfiles.save(new LecturerProfile(lecturer, science, "dr"));

        createUser("Ada", "Admin", "admin@test.pl", Role.ADMIN);

        annaToken = tokenFor("anna@test.pl");
        deanScienceToken = tokenFor("dziekanat.wnsit@test.pl");
        deanMedicineToken = tokenFor("dziekanat.wm@test.pl");
        lecturerToken = tokenFor("piotr@test.pl");
        adminToken = tokenFor("admin@test.pl");
    }

    // zapisy przez dziekanat

    @Test
    void deanOfficeEnrollsStudentInOwnFaculty() throws Exception {
        enroll(deanScienceToken, annaId, computerScienceId, 1)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.albumNumber").value("1001"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void deanOfficeCannotEnrollInFieldOfAnotherFaculty() throws Exception {
        enroll(deanScienceToken, annaId, medicineId, 1).andExpect(status().isNotFound());
    }

    @Test
    void studentCannotEnrollThemselves() throws Exception {
        enroll(annaToken, annaId, computerScienceId, 1).andExpect(status().isForbidden());
    }

    @Test
    void duplicateEnrollmentIsRejected() throws Exception {
        enroll(deanScienceToken, annaId, computerScienceId, 1).andExpect(status().isCreated());

        enroll(deanScienceToken, annaId, computerScienceId, 1).andExpect(status().isConflict());
    }

    @Test
    void semesterBeyondFieldLengthIsRejected() throws Exception {
        enroll(deanScienceToken, annaId, computerScienceId, 8).andExpect(status().isBadRequest());
    }

    // IDOR: odczyt cudzych danych

    @Test
    void studentSeesOnlyOwnStudies() throws Exception {
        enroll(deanScienceToken, annaId, computerScienceId, 1);
        enroll(deanScienceToken, janId, computerScienceId, 1);

        mvc.perform(get("/api/me/studies").header(AUTHORIZATION, bearer(annaToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].studentId").value(annaId));
    }

    @Test
    void studentCannotReadAnotherStudentsStudyById() throws Exception {
        String annaStudy = idOf(enroll(deanScienceToken, annaId, computerScienceId, 1));
        String janStudy = idOf(enroll(deanScienceToken, janId, computerScienceId, 1));

        getStudy(annaStudy, annaToken).andExpect(status().isOk());
        // Anna podmienia ID w adresie na studia Jana
        getStudy(janStudy, annaToken).andExpect(status().isNotFound());
    }

    @Test
    void foreignRecordLooksExactlyLikeNonExistentOne() throws Exception {
        String janStudy = idOf(enroll(deanScienceToken, janId, computerScienceId, 1));

        String foreign = getStudy(janStudy, annaToken).andReturn().getResponse().getContentAsString();
        String missing = getStudy("00000000-0000-0000-0000-000000000000", annaToken)
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(foreign.replace(janStudy, ""))
                .isEqualTo(missing.replace("00000000-0000-0000-0000-000000000000", ""));
    }

    @Test
    void deanOfficeOfAnotherFacultyCannotReadOrModifyStudy() throws Exception {
        String annaStudy = idOf(enroll(deanScienceToken, annaId, computerScienceId, 1));

        getStudy(annaStudy, deanMedicineToken).andExpect(status().isNotFound());
        updateStudy(annaStudy, deanMedicineToken, 2, "ACTIVE").andExpect(status().isNotFound());
    }

    @Test
    void deanOfficeListContainsOnlyOwnFaculty() throws Exception {
        enroll(deanScienceToken, annaId, computerScienceId, 1);
        enroll(deanMedicineToken, janId, medicineId, 1);

        mvc.perform(get("/api/dean-office/studies").header(AUTHORIZATION, bearer(deanScienceToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].studentId").value(annaId));
    }

    @Test
    void lecturerCannotReadStudyAndAdminCan() throws Exception {
        String annaStudy = idOf(enroll(deanScienceToken, annaId, computerScienceId, 1));

        getStudy(annaStudy, lecturerToken).andExpect(status().isNotFound());
        getStudy(annaStudy, adminToken).andExpect(status().isOk());
    }

    // zmiany i audyt

    @Test
    void statusChangeIsAudited() throws Exception {
        String annaStudy = idOf(enroll(deanScienceToken, annaId, computerScienceId, 3));

        updateStudy(annaStudy, deanScienceToken, 3, "DEAN_LEAVE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DEAN_LEAVE"));

        auditHistory("study", annaStudy, adminToken)
                .andExpect(jsonPath("$[0].action").value("UPDATE"))
                .andExpect(jsonPath("$[0].oldValue.status").value("ACTIVE"))
                .andExpect(jsonPath("$[0].newValue.status").value("DEAN_LEAVE"));
    }

    // pomocnicze

    private String createStudent(String firstName, String lastName, String email, String albumNumber) {
        User user = createUser(firstName, lastName, email, Role.STUDENT);
        studentProfiles.save(new StudentProfile(user, albumNumber, null));
        return user.getId().toString();
    }

    private ResultActions enroll(String token, String studentId, String fieldId, int semester) throws Exception {
        return mvc.perform(post("/api/dean-office/studies")
                .header(AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"studentId": "%s", "fieldOfStudyId": "%s", "startYear": 2026, "currentSemester": %d}
                        """.formatted(studentId, fieldId, semester)));
    }

    private ResultActions getStudy(String id, String token) throws Exception {
        return mvc.perform(get("/api/studies/{id}", id).header(AUTHORIZATION, bearer(token)));
    }

    private ResultActions updateStudy(String id, String token, int semester, String status) throws Exception {
        return mvc.perform(put("/api/dean-office/studies/{id}", id)
                .header(AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"currentSemester": %d, "status": "%s"}
                        """.formatted(semester, status)));
    }
}