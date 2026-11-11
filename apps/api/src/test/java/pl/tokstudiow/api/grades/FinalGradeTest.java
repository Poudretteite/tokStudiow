package pl.tokstudiow.api.grades;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import pl.tokstudiow.api.IntegrationTest;
import pl.tokstudiow.api.groups.ClassGroup;
import pl.tokstudiow.api.groups.GroupEnrollment;
import pl.tokstudiow.api.university.*;
import pl.tokstudiow.api.user.Role;
import pl.tokstudiow.api.user.User;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.contains;

class FinalGradeTest extends IntegrationTest {

    private ClassGroup group;
    private GroupEnrollment anna;
    private GroupEnrollment jan;

    private String lecturerId;
    private String lecturerToken;
    private String otherLecturerToken;
    private String annaToken;
    private String janToken;

    @BeforeEach
    void setUp() throws Exception {
        Faculty faculty = fixtures.faculty("Wydział Nauk Ścisłych i Technicznych", "WNŚiT");
        FieldOfStudy field = fixtures.fieldOfStudy(faculty, "Informatyka");
        CourseComponent lab = fixtures.courseComponent(field, "INF-BD", ComponentType.LAB, GradingScaleType.NUMERIC);
        AcademicTerm term = fixtures.term();

        User lecturer = fixtures.lecturer("piotr@test.pl", faculty);
        fixtures.lecturer("marek@test.pl", faculty);
        lecturerId = lecturer.getId().toString();

        group = fixtures.group(lab, term, lecturer, 1);
        anna = fixtures.enroll(group, fixtures.student("anna@test.pl", "1001", field));
        jan = fixtures.enroll(group, fixtures.student("jan@test.pl", "1002", field));

        lecturerToken = tokenFor("piotr@test.pl");
        otherLecturerToken = tokenFor("marek@test.pl");
        annaToken = tokenFor("anna@test.pl");
        janToken = tokenFor("jan@test.pl");
    }

    // szkic i zatwierdzenie

    @Test
    void studentDoesNotSeeDraft() throws Exception {
        saveDraft(anna, "FIRST", "4,0")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));

        transcript(annaToken).andExpect(jsonPath("$[0].entries.length()").value(0));
    }

    @Test
    void draftCanBeChangedBeforeApproval() throws Exception {
        String id = idOf(saveDraft(anna, "FIRST", "3,0"));

        saveDraft(anna, "FIRST", "3,5")
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.gradeLabel").value("3,5"));
    }

    @Test
    void approvedGradeAppearsInTranscriptWithAverage() throws Exception {
        approve(idOf(saveDraft(anna, "FIRST", "4,0"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.approvedBy").value(lecturerId));

        transcript(annaToken)
                .andExpect(jsonPath("$[0].entries.length()").value(1))
                .andExpect(jsonPath("$[0].entries[0].gradeLabel").value("4,0"))
                .andExpect(jsonPath("$[0].average").value(4.0));
    }

    @Test
    void approvedGradeCannotBeChangedOrApprovedAgain() throws Exception {
        String id = idOf(saveDraft(anna, "FIRST", "4,0"));
        approve(id);

        saveDraft(anna, "FIRST", "5,0").andExpect(status().isConflict());
        approve(id).andExpect(status().isConflict());
    }

    // terminy

    @Test
    void retakeRequiresApprovedAndFailedPreviousAttempt() throws Exception {
        saveDraft(anna, "SECOND", "3,0").andExpect(status().isBadRequest());

        approve(idOf(saveDraft(anna, "FIRST", "4,0")));
        saveDraft(anna, "SECOND", "3,0").andExpect(status().isBadRequest());

        approve(idOf(saveDraft(jan, "FIRST", "2,0")));
        saveDraft(jan, "SECOND", "3,0").andExpect(status().isOk());
    }

    @Test
    void averageIncludesFailedFirstAttempt() throws Exception {
        approve(idOf(saveDraft(jan, "FIRST", "2,0")));
        approve(idOf(saveDraft(jan, "SECOND", "4,0")));

        transcript(janToken)
                .andExpect(jsonPath("$[0].entries.length()").value(2))
                .andExpect(jsonPath("$[0].average").value(3.0));
    }

    // propozycja

    @Test
    void proposalIsWeightedAverageRoundedToNearestScaleValue() throws Exception {
        String quiz1 = addItem("Kolokwium 1", "40");
        String quiz2 = addItem("Kolokwium 2", "60");
        issuePartial(anna, quiz1, "3,0");
        issuePartial(anna, quiz2, "5,0");   // 0,4·3,0 + 0,6·5,0 = 4,20 → 4,0
        issuePartial(jan, quiz1, "4,0");    // Jan nie ma oceny z kolokwium 2

        mvc.perform(get("/api/lecturer/groups/{id}/final-grade-proposals", group.getId())
                        .header(AUTHORIZATION, bearer(lecturerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.albumNumber == '1001')].weightedAverage").value(4.2))
                .andExpect(jsonPath("$[?(@.albumNumber == '1001')].proposedGradeLabel").value("4,0"))
                .andExpect(jsonPath("$[?(@.albumNumber == '1002')].proposedGradeLabel", contains(nullValue())));
    }

    // arkusz i zatwierdzanie całej grupy

    @Test
    void sheetShowsProposalAndAttemptToApprove() throws Exception {
        String quiz = addItem("Kolokwium", "100");
        issuePartial(anna, quiz, "4,0");     // Jan bez oceny → brak propozycji

        sheet()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.albumNumber == '1001')].proposedGradeLabel").value("4,0"))
                .andExpect(jsonPath("$[?(@.albumNumber == '1001')].openAttempt").value("FIRST"))
                .andExpect(jsonPath("$[?(@.albumNumber == '1001')].grades[*]", hasSize(0)))
                .andExpect(jsonPath("$[?(@.albumNumber == '1002')].proposedGradeLabel", contains(nullValue())));
    }

    @Test
    void batchApprovalApprovesWholeGroupAtOnce() throws Exception {
        approveBatch(entry(anna, "4,0"), entry(jan, "2,0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].status", contains("APPROVED", "APPROVED")))
                .andExpect(jsonPath("$[*].attempt", contains("FIRST", "FIRST")));

        transcript(annaToken).andExpect(jsonPath("$[0].entries[0].gradeLabel").value("4,0"));
        sheet()
                .andExpect(jsonPath("$[?(@.albumNumber == '1001')].openAttempt", contains(nullValue())))
                .andExpect(jsonPath("$[?(@.albumNumber == '1002')].openAttempt").value("SECOND"));
    }

    @Test
    void batchApprovalUsesExistingDraftAndNextAttemptAfterFailure() throws Exception {
        String draftId = idOf(saveDraft(anna, "FIRST", "3,0"));
        approve(idOf(saveDraft(jan, "FIRST", "2,0")));

        approveBatch(entry(anna, "3,5"), entry(jan, "3,0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(draftId))
                .andExpect(jsonPath("$[0].gradeLabel").value("3,5"))
                .andExpect(jsonPath("$[1].attempt").value("SECOND"));
    }

    @Test
    void settledGradeCannotBeApprovedAgain() throws Exception {
        approveBatch(entry(anna, "4,0"));

        approveBatch(entry(anna, "5,0")).andExpect(status().isConflict());
    }

    @Test
    void batchApprovalIsAtomic() throws Exception {
        String zal = fixtures.gradeValue(GradingScaleType.PASS_FAIL, "zal").getId().toString();

        // ocena spoza skali laboratorium u Jana wycofuje także poprawny wpis Anny
        approveBatch(entry(anna, "4,0"), """
                {"enrollmentId": "%s", "gradeValueId": "%s"}""".formatted(jan.getId(), zal))
                .andExpect(status().isBadRequest());

        sheet().andExpect(jsonPath("$[?(@.albumNumber == '1001')].grades[*]", hasSize(0)));
    }

    @Test
    void batchApprovalRejectsDuplicateStudent() throws Exception {
        approveBatch(entry(anna, "4,0"), entry(anna, "5,0")).andExpect(status().isBadRequest());
    }

    @Test
    void batchApprovalIsAuditedOncePerStudent() throws Exception {
        String id = JsonPath.read(approveBatch(entry(anna, "4,5"))
                .andReturn().getResponse().getContentAsString(), "$[0].id");

        createUser("Ada", "Admin", "admin@test.pl", Role.ADMIN);
        auditHistory("final_grade", id, tokenFor("admin@test.pl"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].action").value("CREATE"))
                .andExpect(jsonPath("$[0].actorId").value(lecturerId))
                .andExpect(jsonPath("$[0].newValue.status").value("APPROVED"))
                .andExpect(jsonPath("$[0].newValue.approvedBy").value(lecturerId));
    }

    @Test
    void otherLecturerAndStudentCannotUseSheetOrBatchApproval() throws Exception {
        approveBatchAs(otherLecturerToken, entry(anna, "5,0")).andExpect(status().isNotFound());
        approveBatchAs(annaToken, entry(anna, "5,0")).andExpect(status().isForbidden());
        mvc.perform(get("/api/lecturer/groups/{id}/final-grade-sheet", group.getId())
                        .header(AUTHORIZATION, bearer(otherLecturerToken)))
                .andExpect(status().isNotFound());
    }

    // ataki

    @Test
    void otherLecturerCannotSaveOrApproveGrade() throws Exception {
        String id = idOf(saveDraft(anna, "FIRST", "4,0"));

        saveDraft(otherLecturerToken, anna, "FIRST", "5,0").andExpect(status().isNotFound());
        mvc.perform(post("/api/lecturer/groups/{g}/final-grades/{id}/approve", group.getId(), id)
                        .header(AUTHORIZATION, bearer(otherLecturerToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void studentCannotApproveOwnGrade() throws Exception {
        String id = idOf(saveDraft(anna, "FIRST", "5,0"));

        mvc.perform(post("/api/lecturer/groups/{g}/final-grades/{id}/approve", group.getId(), id)
                        .header(AUTHORIZATION, bearer(annaToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void approvalIsAuditedWithApprover() throws Exception {
        String id = idOf(saveDraft(anna, "FIRST", "4,5"));
        approve(id);

        createUser("Ada", "Admin", "admin@test.pl", Role.ADMIN);
        auditHistory("final_grade", id, tokenFor("admin@test.pl"))
                .andExpect(jsonPath("$[0].action").value("UPDATE"))
                .andExpect(jsonPath("$[0].actorId").value(lecturerId))
                .andExpect(jsonPath("$[0].oldValue.status").value("DRAFT"))
                .andExpect(jsonPath("$[0].newValue.status").value("APPROVED"))
                .andExpect(jsonPath("$[0].newValue.approvedBy").value(lecturerId));
    }

    // pomocnicze

    private ResultActions saveDraft(GroupEnrollment enrollment, String attempt, String label) throws Exception {
        return saveDraft(lecturerToken, enrollment, attempt, label);
    }

    private ResultActions saveDraft(String token, GroupEnrollment enrollment, String attempt, String label)
            throws Exception {
        return mvc.perform(put("/api/lecturer/groups/{id}/final-grades", group.getId())
                .header(AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"enrollmentId": "%s", "attempt": "%s", "gradeValueId": "%s"}
                        """.formatted(enrollment.getId(), attempt, numeric(label))));
    }

    private ResultActions approve(String finalGradeId) throws Exception {
        return mvc.perform(post("/api/lecturer/groups/{g}/final-grades/{id}/approve", group.getId(), finalGradeId)
                .header(AUTHORIZATION, bearer(lecturerToken)));
    }

    private ResultActions sheet() throws Exception {
        return mvc.perform(get("/api/lecturer/groups/{id}/final-grade-sheet", group.getId())
                .header(AUTHORIZATION, bearer(lecturerToken)));
    }

    private String entry(GroupEnrollment enrollment, String label) {
        return """
                {"enrollmentId": "%s", "gradeValueId": "%s"}""".formatted(enrollment.getId(), numeric(label));
    }

    private ResultActions approveBatch(String... entries) throws Exception {
        return approveBatchAs(lecturerToken, entries);
    }

    private ResultActions approveBatchAs(String token, String... entries) throws Exception {
        return mvc.perform(post("/api/lecturer/groups/{id}/final-grades/approvals", group.getId())
                .header(AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"entries\": [" + String.join(", ", entries) + "]}"));
    }

    private ResultActions transcript(String token) throws Exception {
        return mvc.perform(get("/api/me/transcript").header(AUTHORIZATION, bearer(token)));
    }

    private String addItem(String name, String weight) throws Exception {
        return idOf(mvc.perform(post("/api/lecturer/groups/{id}/assessment-items", group.getId())
                .header(AUTHORIZATION, bearer(lecturerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "%s", "weightPercent": %s}
                        """.formatted(name, weight))));
    }

    private void issuePartial(GroupEnrollment enrollment, String itemId, String label) throws Exception {
        mvc.perform(post("/api/lecturer/groups/{id}/partial-grades", group.getId())
                        .header(AUTHORIZATION, bearer(lecturerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"enrollmentId": "%s", "assessmentItemId": "%s", "gradeValueId": "%s"}
                                """.formatted(enrollment.getId(), itemId, numeric(label))))
                .andExpect(status().isCreated());
    }

    private String numeric(String label) {
        return fixtures.gradeValue(GradingScaleType.NUMERIC, label).getId().toString();
    }

    @Test
    void failedPassFailRequirementProposesFailingGrade() throws Exception {
        String quiz = addItem("Kolokwium", "100");
        String reports = idOf(mvc.perform(post("/api/lecturer/groups/{id}/assessment-items", group.getId())
                .header(AUTHORIZATION, bearer(lecturerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "Sprawozdania", "gradingType": "PASS_FAIL"}
                        """)));
        String zal = fixtures.gradeValue(GradingScaleType.PASS_FAIL, "zal").getId().toString();
        String nzal = fixtures.gradeValue(GradingScaleType.PASS_FAIL, "nzal").getId().toString();

        issuePartial(anna, quiz, "5,0");
        issuePartialValue(anna, reports, nzal);   // świetne kolokwium, ale brak sprawozdań → 2,0
        issuePartial(jan, quiz, "4,0");
        issuePartialValue(jan, reports, zal);     // warunek spełniony → propozycja ze średniej

        mvc.perform(get("/api/lecturer/groups/{id}/final-grade-proposals", group.getId())
                        .header(AUTHORIZATION, bearer(lecturerToken)))
                .andExpect(jsonPath("$[?(@.albumNumber == '1001')].proposedGradeLabel").value("2,0"))
                .andExpect(jsonPath("$[?(@.albumNumber == '1002')].proposedGradeLabel").value("4,0"));
    }

    private void issuePartialValue(GroupEnrollment enrollment, String itemId, String gradeValueId) throws Exception {
        mvc.perform(post("/api/lecturer/groups/{id}/partial-grades", group.getId())
                        .header(AUTHORIZATION, bearer(lecturerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"enrollmentId": "%s", "assessmentItemId": "%s", "gradeValueId": "%s"}
                                """.formatted(enrollment.getId(), itemId, gradeValueId)))
                .andExpect(status().isCreated());
    }
}