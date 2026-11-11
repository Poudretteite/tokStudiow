package pl.tokstudiow.api.groups;

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
import pl.tokstudiow.api.studies.Study;
import pl.tokstudiow.api.studies.StudyRepository;
import pl.tokstudiow.api.university.*;
import pl.tokstudiow.api.user.Role;
import pl.tokstudiow.api.user.User;

import java.time.LocalDate;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ClassGroupTest extends IntegrationTest {

    @Autowired FacultyRepository faculties;
    @Autowired FieldOfStudyRepository fields;
    @Autowired CourseRepository courses;
    @Autowired GradingScaleRepository scales;
    @Autowired AcademicTermRepository terms;
    @Autowired StudentProfileRepository studentProfiles;
    @Autowired LecturerProfileRepository lecturerProfiles;
    @Autowired DeanOfficeProfileRepository deanOfficeProfiles;
    @Autowired StudyRepository studies;

    private String labComponentId;
    private String termId;
    private String lecturerId;
    private String annaStudyId;
    private String janStudyId;

    private String deanToken;
    private String otherDeanToken;
    private String lecturerToken;
    private String otherLecturerToken;
    private String annaToken;

    @BeforeEach
    void setUp() throws Exception {
        Faculty science = faculties.save(new Faculty("Wydział Nauk Ścisłych i Technicznych", "WNŚiT"));
        Faculty medicine = faculties.save(new Faculty("Wydział Medyczny", "WM"));
        FieldOfStudy computerScience = fields.save(new FieldOfStudy(
                science, "Informatyka", StudyLevel.FIRST_CYCLE, StudyMode.FULL_TIME, (short) 7));

        Course databases = new Course(computerScience, "INF-BD", "Bazy danych", (short) 5, (short) 3);
        databases.addComponent(ComponentType.LAB, (short) 30, scales.findAll().getFirst());
        courses.save(databases);
        labComponentId = databases.getComponents().getFirst().getId().toString();

        termId = terms.save(new AcademicTerm("2026/2027 zimowy", TermType.WINTER,
                LocalDate.of(2026, 10, 1), LocalDate.of(2027, 2, 20))).getId().toString();

        User lecturer = createUser("Piotr", "Nowicki", "piotr@test.pl", Role.LECTURER);
        lecturerProfiles.save(new LecturerProfile(lecturer, science, "dr"));
        lecturerId = lecturer.getId().toString();
        User otherLecturer = createUser("Marek", "Inny", "marek@test.pl", Role.LECTURER);
        lecturerProfiles.save(new LecturerProfile(otherLecturer, science, "dr"));

        User dean = createUser("Ewa", "Dziekanat", "dziekanat.wnsit@test.pl", Role.DEAN_OFFICE);
        deanOfficeProfiles.save(new DeanOfficeProfile(dean, science));
        User otherDean = createUser("Olga", "Dziekanat", "dziekanat.wm@test.pl", Role.DEAN_OFFICE);
        deanOfficeProfiles.save(new DeanOfficeProfile(otherDean, medicine));

        annaStudyId = createStudentWithStudy("Anna", "Nowak", "anna@test.pl", "1001", computerScience);
        janStudyId = createStudentWithStudy("Jan", "Kowalski", "jan@test.pl", "1002", computerScience);

        deanToken = tokenFor("dziekanat.wnsit@test.pl");
        otherDeanToken = tokenFor("dziekanat.wm@test.pl");
        lecturerToken = tokenFor("piotr@test.pl");
        otherLecturerToken = tokenFor("marek@test.pl");
        annaToken = tokenFor("anna@test.pl");
    }

    // tworzenie grup

    @Test
    void deanOfficeCreatesGroupForOwnFacultyCourse() throws Exception {
        createGroup(deanToken, 1, 20)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.courseCode").value("INF-BD"))
                .andExpect(jsonPath("$.lecturerId").value(lecturerId))
                .andExpect(jsonPath("$.gradingScaleId").isNotEmpty());
    }

    @Test
    void deanOfficeOfAnotherFacultyCannotCreateGroup() throws Exception {
        createGroup(otherDeanToken, 1, 20).andExpect(status().isNotFound());
    }

    // zapisy i reguły

    @Test
    void lecturerSeesRosterOfOwnGroup() throws Exception {
        String groupId = idOf(createGroup(deanToken, 1, 20));
        enroll(groupId, annaStudyId).andExpect(status().isCreated());

        roster(groupId, lecturerToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].albumNumber").value("1001"))
                .andExpect(jsonPath("$[0].email").doesNotExist());
    }

    @Test
    void groupCapacityIsEnforced() throws Exception {
        String groupId = idOf(createGroup(deanToken, 1, 1));

        enroll(groupId, annaStudyId).andExpect(status().isCreated());
        enroll(groupId, janStudyId).andExpect(status().isConflict());
    }

    @Test
    void studentCannotBeInTwoGroupsOfSameComponentInOneTerm() throws Exception {
        String group1 = idOf(createGroup(deanToken, 1, 20));
        String group2 = idOf(createGroup(deanToken, 2, 20));

        enroll(group1, annaStudyId).andExpect(status().isCreated());
        enroll(group2, annaStudyId).andExpect(status().isConflict());
    }

    @Test
    void studentOnDeanLeaveCannotBeEnrolled() throws Exception {
        String groupId = idOf(createGroup(deanToken, 1, 20));
        mvc.perform(put("/api/dean-office/studies/{id}", annaStudyId)
                        .header(AUTHORIZATION, bearer(deanToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentSemester": 3, "status": "DEAN_LEAVE"}
                                """))
                .andExpect(status().isOk());

        enroll(groupId, annaStudyId).andExpect(status().isBadRequest());
    }

    @Test
    void enrollmentIsAudited() throws Exception {
        String groupId = idOf(createGroup(deanToken, 1, 20));
        String enrollmentId = JsonPathHelper.read(enroll(groupId, annaStudyId), "$.enrollmentId");

        createUser("Ada", "Admin", "admin@test.pl", Role.ADMIN);
        auditHistory("group_enrollment", enrollmentId, tokenFor("admin@test.pl"))
                .andExpect(jsonPath("$[0].action").value("CREATE"))
                .andExpect(jsonPath("$[0].newValue.studyId").value(annaStudyId));
    }

    // kontrola dostępu

    @Test
    void otherLecturerAndStudentCannotSeeRoster() throws Exception {
        String groupId = idOf(createGroup(deanToken, 1, 20));
        enroll(groupId, annaStudyId);

        roster(groupId, otherLecturerToken).andExpect(status().isNotFound());
        roster(groupId, annaToken).andExpect(status().isNotFound());
    }

    @Test
    void eachUserSeesOnlyOwnGroups() throws Exception {
        String groupId = idOf(createGroup(deanToken, 1, 20));
        enroll(groupId, annaStudyId);

        mvc.perform(get("/api/lecturer/groups").header(AUTHORIZATION, bearer(lecturerToken)))
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/lecturer/groups").header(AUTHORIZATION, bearer(otherLecturerToken)))
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/me/groups").header(AUTHORIZATION, bearer(annaToken)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(groupId));
    }

    @Test
    void lecturerCanReadStudyOnlyOfOwnStudents() throws Exception {
        String groupId = idOf(createGroup(deanToken, 1, 20));

        getStudy(annaStudyId, lecturerToken).andExpect(status().isNotFound());

        enroll(groupId, annaStudyId);

        getStudy(annaStudyId, lecturerToken).andExpect(status().isOk());
        getStudy(annaStudyId, otherLecturerToken).andExpect(status().isNotFound());
        getStudy(janStudyId, lecturerToken).andExpect(status().isNotFound());
    }

    // pomocnicze

    private String createStudentWithStudy(String firstName, String lastName, String email,
                                          String albumNumber, FieldOfStudy field) {
        User user = createUser(firstName, lastName, email, Role.STUDENT);
        StudentProfile profile = studentProfiles.save(new StudentProfile(user, albumNumber, null));
        return studies.save(new Study(profile, field, (short) 2026, (short) 3)).getId().toString();
    }

    private ResultActions createGroup(String token, int number, int capacity) throws Exception {
        return mvc.perform(post("/api/dean-office/groups")
                .header(AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"courseComponentId": "%s", "academicTermId": "%s", "lecturerId": "%s",
                         "groupNumber": %d, "capacity": %d}
                        """.formatted(labComponentId, termId, lecturerId, number, capacity)));
    }

    private ResultActions enroll(String groupId, String studyId) throws Exception {
        return mvc.perform(post("/api/dean-office/groups/{id}/enrollments", groupId)
                .header(AUTHORIZATION, bearer(deanToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"studyId": "%s"}
                        """.formatted(studyId)));
    }

    private ResultActions roster(String groupId, String token) throws Exception {
        return mvc.perform(get("/api/groups/{id}/students", groupId).header(AUTHORIZATION, bearer(token)));
    }

    private ResultActions getStudy(String studyId, String token) throws Exception {
        return mvc.perform(get("/api/studies/{id}", studyId).header(AUTHORIZATION, bearer(token)));
    }

    private static final class JsonPathHelper {
        static String read(ResultActions result, String path) throws Exception {
            return com.jayway.jsonpath.JsonPath.read(result.andReturn().getResponse().getContentAsString(), path);
        }
    }
}