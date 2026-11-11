package pl.tokstudiow.api.grades;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import pl.tokstudiow.api.IntegrationTest;
import pl.tokstudiow.api.groups.ClassGroup;
import pl.tokstudiow.api.groups.GroupEnrollment;
import pl.tokstudiow.api.studies.Study;
import pl.tokstudiow.api.university.*;
import pl.tokstudiow.api.user.Role;
import pl.tokstudiow.api.user.User;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

class PartialGradeTest extends IntegrationTest {

    private ClassGroup group;
    private ClassGroup otherGroup;
    private GroupEnrollment annaEnrollment;
    private GroupEnrollment janEnrollment;
    private GroupEnrollment olaInOtherGroup;

    private String lecturerToken;
    private String otherLecturerToken;
    private String annaToken;
    private String olaToken;

    @BeforeEach
    void setUp() throws Exception {
        Faculty faculty = fixtures.faculty("Wydział Nauk Ścisłych i Technicznych", "WNŚiT");
        FieldOfStudy field = fixtures.fieldOfStudy(faculty, "Informatyka");
        CourseComponent lab = fixtures.courseComponent(field, "INF-BD", ComponentType.LAB, GradingScaleType.NUMERIC);
        AcademicTerm term = fixtures.term();

        User lecturer = fixtures.lecturer("piotr@test.pl", faculty);
        User otherLecturer = fixtures.lecturer("marek@test.pl", faculty);

        group = fixtures.group(lab, term, lecturer, 1);
        otherGroup = fixtures.group(lab, term, otherLecturer, 2);

        Study anna = fixtures.student("anna@test.pl", "1001", field);
        Study jan = fixtures.student("jan@test.pl", "1002", field);
        Study ola = fixtures.student("ola@test.pl", "1003", field);
        annaEnrollment = fixtures.enroll(group, anna);
        janEnrollment = fixtures.enroll(group, jan);
        olaInOtherGroup = fixtures.enroll(otherGroup, ola);

        lecturerToken = tokenFor("piotr@test.pl");
        otherLecturerToken = tokenFor("marek@test.pl");
        annaToken = tokenFor("anna@test.pl");
        olaToken = tokenFor("ola@test.pl");
    }

    // elementy zaliczenia

    @Test
    void lecturerAddsAssessmentItemsInOrder() throws Exception {
        addItem(lecturerToken, group, "Kolokwium 1", "40").andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderIndex").value(1));
        addItem(lecturerToken, group, "Kolokwium 2", "60").andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderIndex").value(2));

        mvc.perform(get("/api/groups/{id}/assessment-items", group.getId()).header(AUTHORIZATION, bearer(annaToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void totalWeightCannotExceed100Percent() throws Exception {
        addItem(lecturerToken, group, "Kolokwium 1", "60").andExpect(status().isCreated());

        addItem(lecturerToken, group, "Kolokwium 2", "50").andExpect(status().isBadRequest());
    }

    @Test
    void otherLecturerCannotAddItemsToGroup() throws Exception {
        addItem(otherLecturerToken, group, "Kolokwium", "40").andExpect(status().isNotFound());
    }

    // wystawianie ocen

    @Test
    void lecturerIssuesPartialGrade() throws Exception {
        String itemId = idOf(addItem(lecturerToken, group, "Kolokwium 1", "40"));

        issue(lecturerToken, group, annaEnrollment.getId().toString(), itemId, numeric("4,5"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.gradeLabel").value("4,5"))
                .andExpect(jsonPath("$.numericValue").value(4.5))
                .andExpect(jsonPath("$.attempt").value(1));
    }

    @Test
    void retakeCreatesNextAttemptAndKeepsHistory() throws Exception {
        String itemId = idOf(addItem(lecturerToken, group, "Kolokwium 1", "40"));
        String enrollmentId = annaEnrollment.getId().toString();

        issue(lecturerToken, group, enrollmentId, itemId, numeric("2,0")).andExpect(status().isCreated());
        issue(lecturerToken, group, enrollmentId, itemId, numeric("4,0"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attempt").value(2));

        myGrades(annaToken, group)
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].gradeLabel").value("2,0"))
                .andExpect(jsonPath("$[1].gradeLabel").value("4,0"));
    }

    @Test
    void gradeFromWrongScaleIsRejected() throws Exception {
        String itemId = idOf(addItem(lecturerToken, group, "Kolokwium 1", "40"));
        String passValue = fixtures.gradeValue(GradingScaleType.PASS_FAIL, "zal").getId().toString();

        issue(lecturerToken, group, annaEnrollment.getId().toString(), itemId, passValue)
                .andExpect(status().isBadRequest());
    }

    // ataki

    @Test
    void otherLecturerCannotIssueGradeInGroup() throws Exception {
        String itemId = idOf(addItem(lecturerToken, group, "Kolokwium 1", "40"));

        issue(otherLecturerToken, group, annaEnrollment.getId().toString(), itemId, numeric("5,0"))
                .andExpect(status().isNotFound());
    }

    @Test
    void studentCannotIssueGrade() throws Exception {
        String itemId = idOf(addItem(lecturerToken, group, "Kolokwium 1", "40"));

        issue(annaToken, group, annaEnrollment.getId().toString(), itemId, numeric("5,0"))
                .andExpect(status().isForbidden());
    }

    @Test
    void enrollmentFromAnotherGroupIsRejected() throws Exception {
        String itemId = idOf(addItem(lecturerToken, group, "Kolokwium 1", "40"));

        // prowadzący zna ID zapisu Oli z innej grupy i próbuje wystawić jej ocenę przez swoją grupę
        issue(lecturerToken, group, olaInOtherGroup.getId().toString(), itemId, numeric("2,0"))
                .andExpect(status().isNotFound());
    }

    @Test
    void studentSeesOnlyOwnGrades() throws Exception {
        String itemId = idOf(addItem(lecturerToken, group, "Kolokwium 1", "40"));
        issue(lecturerToken, group, annaEnrollment.getId().toString(), itemId, numeric("4,5"));
        issue(lecturerToken, group, janEnrollment.getId().toString(), itemId, numeric("3,0"));

        myGrades(annaToken, group)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].albumNumber").value("1001"));
    }

    @Test
    void studentCannotReadGradesOfGroupTheyAreNotIn() throws Exception {
        myGrades(olaToken, group).andExpect(status().isNotFound());
    }

    @Test
    void issuedGradeIsAuditedWithIssuer() throws Exception {
        String itemId = idOf(addItem(lecturerToken, group, "Kolokwium 1", "40"));
        String gradeId = idOf(issue(lecturerToken, group, annaEnrollment.getId().toString(), itemId, numeric("4,5")));
        String lecturerId = JsonPath.read(mvc.perform(get("/api/me").header(AUTHORIZATION, bearer(lecturerToken)))
                .andReturn().getResponse().getContentAsString(), "$.id");

        createUser("Ada", "Admin", "admin@test.pl", Role.ADMIN);
        auditHistory("partial_grade", gradeId, tokenFor("admin@test.pl"))
                .andExpect(jsonPath("$[0].action").value("CREATE"))
                .andExpect(jsonPath("$[0].actorId").value(lecturerId))
                .andExpect(jsonPath("$[0].newValue.grade").value("4,5"));
    }

    // pomocnicze

    private String numeric(String label) {
        return fixtures.gradeValue(GradingScaleType.NUMERIC, label).getId().toString();
    }

    private ResultActions addItem(String token, ClassGroup g, String name, String weight) throws Exception {
        return mvc.perform(post("/api/lecturer/groups/{id}/assessment-items", g.getId())
                .header(AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "%s", "weightPercent": %s}
                        """.formatted(name, weight)));
    }

    private ResultActions issue(String token, ClassGroup g, String enrollmentId, String itemId, String gradeValueId)
            throws Exception {
        return mvc.perform(post("/api/lecturer/groups/{id}/partial-grades", g.getId())
                .header(AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"enrollmentId": "%s", "assessmentItemId": "%s", "gradeValueId": "%s"}
                        """.formatted(enrollmentId, itemId, gradeValueId)));
    }

    private ResultActions myGrades(String token, ClassGroup g) throws Exception {
        return mvc.perform(get("/api/me/groups/{id}/partial-grades", g.getId()).header(AUTHORIZATION, bearer(token)));
    }

    @Test
    void lecturerCorrectsMistypedGradeWithReason() throws Exception {
        String itemId = idOf(addItem(lecturerToken, group, "Kolokwium 1", "40"));
        String gradeId = idOf(issue(lecturerToken, group, annaEnrollment.getId().toString(), itemId, numeric("3,0")));

        correct(lecturerToken, gradeId, numeric("3,5"), "Pomyłka przy wpisywaniu")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gradeLabel").value("3,5"))
                .andExpect(jsonPath("$.attempt").value(1));

        // korekta nie tworzy nowej próby
        myGrades(annaToken, group)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].gradeLabel").value("3,5"));

        createUser("Ada", "Admin", "admin@test.pl", Role.ADMIN);
        auditHistory("partial_grade", gradeId, tokenFor("admin@test.pl"))
                .andExpect(jsonPath("$[0].action").value("UPDATE"))
                .andExpect(jsonPath("$[0].oldValue.grade").value("3,0"))
                .andExpect(jsonPath("$[0].newValue.grade").value("3,5"))
                .andExpect(jsonPath("$[0].newValue.reason").value("Pomyłka przy wpisywaniu"));
    }

    @Test
    void correctionRequiresReason() throws Exception {
        String itemId = idOf(addItem(lecturerToken, group, "Kolokwium 1", "40"));
        String gradeId = idOf(issue(lecturerToken, group, annaEnrollment.getId().toString(), itemId, numeric("3,0")));

        correct(lecturerToken, gradeId, numeric("3,5"), "").andExpect(status().isBadRequest());
    }

    @Test
    void otherLecturerCannotCorrectGrade() throws Exception {
        String itemId = idOf(addItem(lecturerToken, group, "Kolokwium 1", "40"));
        String gradeId = idOf(issue(lecturerToken, group, annaEnrollment.getId().toString(), itemId, numeric("3,0")));

        mvc.perform(patch("/api/lecturer/groups/{g}/partial-grades/{id}", group.getId(), gradeId)
                        .header(AUTHORIZATION, bearer(otherLecturerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(correctionJson(numeric("5,0"), "Próba zmiany")))
                .andExpect(status().isNotFound());
    }

    @Test
    void partialGradesAreLockedAfterFinalGradeApproval() throws Exception {
        String itemId = idOf(addItem(lecturerToken, group, "Kolokwium 1", "40"));
        String gradeId = idOf(issue(lecturerToken, group, annaEnrollment.getId().toString(), itemId, numeric("3,0")));

        String finalId = idOf(mvc.perform(put("/api/lecturer/groups/{id}/final-grades", group.getId())
                .header(AUTHORIZATION, bearer(lecturerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"enrollmentId": "%s", "attempt": "FIRST", "gradeValueId": "%s"}
                        """.formatted(annaEnrollment.getId(), numeric("3,0")))));
        mvc.perform(post("/api/lecturer/groups/{g}/final-grades/{id}/approve", group.getId(), finalId)
                .header(AUTHORIZATION, bearer(lecturerToken)));

        correct(lecturerToken, gradeId, numeric("4,0"), "Zmiana po zatwierdzeniu")
                .andExpect(status().isConflict());
    }

    private ResultActions correct(String token, String gradeId, String gradeValueId, String reason) throws Exception {
        return mvc.perform(patch("/api/lecturer/groups/{g}/partial-grades/{id}", group.getId(), gradeId)
                .header(AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(correctionJson(gradeValueId, reason)));
    }

    private static String correctionJson(String gradeValueId, String reason) {
        return """
                {"gradeValueId": "%s", "reason": "%s"}
                """.formatted(gradeValueId, reason);
    }

    @Test
    void passFailItemHasNoWeightAndAcceptsOnlyPassFailValues() throws Exception {
        String itemId = idOf(mvc.perform(post("/api/lecturer/groups/{id}/assessment-items", group.getId())
                        .header(AUTHORIZATION, bearer(lecturerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Sprawozdania", "gradingType": "PASS_FAIL"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.gradingType").value("PASS_FAIL"))
                .andExpect(jsonPath("$.weightPercent").value(0)));

        String zal = fixtures.gradeValue(GradingScaleType.PASS_FAIL, "zal").getId().toString();
        issue(lecturerToken, group, annaEnrollment.getId().toString(), itemId, zal).andExpect(status().isCreated());
        issue(lecturerToken, group, janEnrollment.getId().toString(), itemId, numeric("4,0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void gradedItemRequiresWeight() throws Exception {
        mvc.perform(post("/api/lecturer/groups/{id}/assessment-items", group.getId())
                        .header(AUTHORIZATION, bearer(lecturerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Kolokwium", "gradingType": "NUMERIC"}
                                """))
                .andExpect(status().isBadRequest());
    }
}