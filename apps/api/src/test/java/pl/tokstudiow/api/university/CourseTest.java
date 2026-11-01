package pl.tokstudiow.api.university;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import pl.tokstudiow.api.IntegrationTest;
import pl.tokstudiow.api.user.Role;

import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CourseTest extends IntegrationTest {

    @Autowired FacultyRepository faculties;
    @Autowired FieldOfStudyRepository fields;
    @Autowired GradingScaleRepository scales;

    private String adminToken;
    private String studentToken;
    private String fieldId;
    private String numericScaleId;
    private String passFailScaleId;

    @BeforeEach
    void setUp() throws Exception {
        createUser("Ada", "Admin", "admin@test.pl", Role.ADMIN);
        createUser("Anna", "Nowak", "anna@test.pl", Role.STUDENT);
        adminToken = tokenFor("admin@test.pl");
        studentToken = tokenFor("anna@test.pl");

        Faculty faculty = faculties.save(new Faculty("Wydział Nauk Ścisłych i Technicznych", "WNŚiT"));
        fieldId = fields.save(new FieldOfStudy(
                faculty, "Informatyka", StudyLevel.FIRST_CYCLE, StudyMode.FULL_TIME, (short) 7)).getId().toString();

        numericScaleId = scaleId(GradingScaleType.NUMERIC);
        passFailScaleId = scaleId(GradingScaleType.PASS_FAIL);
    }

    // skale ocen

    @Test
    void gradingScalesFromMigrationAreAvailable() throws Exception {
        mvc.perform(get("/api/grading-scales").header(AUTHORIZATION, bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.type == 'NUMERIC')].values[*].label",
                        contains("2,0", "3,0", "3,5", "4,0", "4,5", "5,0")))
                .andExpect(jsonPath("$[?(@.type == 'PASS_FAIL')].values[*].label",
                        contains("zal", "nzal")));
    }

    // przedmioty

    @Test
    void adminCreatesCourseVisibleToStudents() throws Exception {
        createCourse(courseJson("INF-BD", 3,
                component("LECTURE", 30, passFailScaleId), component("LAB", 30, numericScaleId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("INF-BD"))
                .andExpect(jsonPath("$.components.length()").value(2));

        mvc.perform(get("/api/fields-of-study/{id}/courses", fieldId).header(AUTHORIZATION, bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].components.length()").value(2));
    }

    @Test
    void courseCodeIsNormalizedAndUnique() throws Exception {
        createCourse(courseJson("inf-bd", 3, component("LAB", 30, numericScaleId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("INF-BD"));

        createCourse(courseJson("INF-BD", 4, component("LAB", 30, numericScaleId)))
                .andExpect(status().isConflict());
    }

    @Test
    void semesterBeyondFieldOfStudyLengthIsRejected() throws Exception {
        createCourse(courseJson("INF-X", 8, component("LAB", 30, numericScaleId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void duplicateComponentTypeIsRejected() throws Exception {
        createCourse(courseJson("INF-X", 3,
                component("LAB", 30, numericScaleId), component("LAB", 15, numericScaleId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void courseWithoutComponentsIsRejected() throws Exception {
        createCourse(courseJson("INF-X", 3)).andExpect(status().isBadRequest());
    }

    @Test
    void unknownGradingScaleReturns404() throws Exception {
        createCourse(courseJson("INF-X", 3, component("LAB", 30, UUID.randomUUID().toString())))
                .andExpect(status().isNotFound());
    }

    @Test
    void studentCannotCreateCourse() throws Exception {
        mvc.perform(post("/api/admin/courses")
                        .header(AUTHORIZATION, bearer(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(courseJson("INF-X", 3, component("LAB", 30, numericScaleId))))
                .andExpect(status().isForbidden());
    }

    @Test
    void creatingCourseIsAuditedWithComponents() throws Exception {
        String id = idOf(createCourse(courseJson("INF-BD", 3,
                component("LECTURE", 30, passFailScaleId), component("LAB", 30, numericScaleId))));

        auditHistory("course", id, adminToken)
                .andExpect(jsonPath("$[0].action").value("CREATE"))
                .andExpect(jsonPath("$[0].newValue.code").value("INF-BD"))
                .andExpect(jsonPath("$[0].newValue.components.length()").value(2));
    }

    // pomocnicze

    private String scaleId(GradingScaleType type) {
        return scales.findAllWithValues().stream()
                .filter(s -> s.getType() == type)
                .findFirst().orElseThrow()
                .getId().toString();
    }

    private ResultActions createCourse(String body) throws Exception {
        return mvc.perform(post("/api/admin/courses")
                .header(AUTHORIZATION, bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String courseJson(String code, int semester, String... components) {
        return """
                {"fieldOfStudyId": "%s", "code": "%s", "name": "Bazy danych",
                 "ects": 5, "semesterNumber": %d, "components": [%s]}
                """.formatted(fieldId, code, semester, String.join(",", components));
    }

    private static String component(String type, int hours, String gradingScaleId) {
        return """
                {"type": "%s", "hours": %d, "gradingScaleId": "%s"}
                """.formatted(type, hours, gradingScaleId);
    }
}