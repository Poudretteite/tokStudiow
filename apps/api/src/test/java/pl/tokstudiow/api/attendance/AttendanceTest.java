package pl.tokstudiow.api.attendance;

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

import java.time.LocalDate;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AttendanceTest extends IntegrationTest {

    private static final LocalDate LAST_WEEK = LocalDate.now().minusWeeks(1);
    private static final LocalDate NEXT_WEEK = LocalDate.now().plusWeeks(1);

    private ClassGroup group;
    private GroupEnrollment anna;
    private GroupEnrollment jan;
    private GroupEnrollment olaInOtherGroup;

    private String lecturerToken;
    private String otherLecturerToken;
    private String annaToken;

    @BeforeEach
    void setUp() throws Exception {
        Faculty faculty = fixtures.faculty("Wydział Nauk Ścisłych i Technicznych", "WNŚiT");
        FieldOfStudy field = fixtures.fieldOfStudy(faculty, "Informatyka");
        CourseComponent lab = fixtures.courseComponent(field, "INF-BD", ComponentType.LAB, GradingScaleType.NUMERIC);
        AcademicTerm term = fixtures.term();

        User lecturer = fixtures.lecturer("piotr@test.pl", faculty);
        User otherLecturer = fixtures.lecturer("marek@test.pl", faculty);
        group = fixtures.group(lab, term, lecturer, 1);
        ClassGroup otherGroup = fixtures.group(lab, term, otherLecturer, 2);

        anna = fixtures.enroll(group, fixtures.student("anna@test.pl", "1001", field));
        jan = fixtures.enroll(group, fixtures.student("jan@test.pl", "1002", field));
        olaInOtherGroup = fixtures.enroll(otherGroup, fixtures.student("ola@test.pl", "1003", field));

        lecturerToken = tokenFor("piotr@test.pl");
        otherLecturerToken = tokenFor("marek@test.pl");
        annaToken = tokenFor("anna@test.pl");
    }

    // zajęcia

    @Test
    void lecturerCreatesSessionWithinTerm() throws Exception {
        createSession(lecturerToken, LAST_WEEK, "10:15", "11:45")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.date").value(LAST_WEEK.toString()))
                .andExpect(jsonPath("$.cancelled").value(false));
    }

    @Test
    void sessionOutsideTermIsRejected() throws Exception {
        createSession(lecturerToken, LocalDate.now().plusYears(1), "10:15", "11:45")
                .andExpect(status().isBadRequest());
    }

    @Test
    void endTimeBeforeStartTimeIsRejected() throws Exception {
        createSession(lecturerToken, LAST_WEEK, "11:45", "10:15").andExpect(status().isBadRequest());
    }

    // obecności

    @Test
    void lecturerRecordsAttendanceAndStudentSeesOnlyOwn() throws Exception {
        String sessionId = idOf(createSession(lecturerToken, LAST_WEEK, "10:15", "11:45"));

        record(lecturerToken, sessionId, entry(anna, "PRESENT"), entry(jan, "ABSENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mvc.perform(get("/api/me/groups/{id}/attendance", group.getId()).header(AUTHORIZATION, bearer(annaToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("PRESENT"));
    }

    @Test
    void statusChangeIsAuditedWithPreviousValue() throws Exception {
        String sessionId = idOf(createSession(lecturerToken, LAST_WEEK, "10:15", "11:45"));
        record(lecturerToken, sessionId, entry(jan, "ABSENT"));

        String attendanceId = com.jayway.jsonpath.JsonPath.read(
                record(lecturerToken, sessionId, entry(jan, "EXCUSED"))
                        .andReturn().getResponse().getContentAsString(),
                "$[0].id");

        createUser("Ada", "Admin", "admin@test.pl", Role.ADMIN);
        auditHistory("attendance", attendanceId, tokenFor("admin@test.pl"))
                .andExpect(jsonPath("$[0].action").value("UPDATE"))
                .andExpect(jsonPath("$[0].oldValue.status").value("ABSENT"))
                .andExpect(jsonPath("$[0].newValue.status").value("EXCUSED"));
    }

    @Test
    void attendanceCannotBeRecordedBeforeSession() throws Exception {
        String sessionId = idOf(createSession(lecturerToken, NEXT_WEEK, "10:15", "11:45"));

        record(lecturerToken, sessionId, entry(anna, "PRESENT")).andExpect(status().isBadRequest());
    }

    @Test
    void duplicateStudentInRequestIsRejected() throws Exception {
        String sessionId = idOf(createSession(lecturerToken, LAST_WEEK, "10:15", "11:45"));

        record(lecturerToken, sessionId, entry(anna, "PRESENT"), entry(anna, "ABSENT"))
                .andExpect(status().isBadRequest());
    }

    // ataki

    @Test
    void otherLecturerCannotRecordAttendance() throws Exception {
        String sessionId = idOf(createSession(lecturerToken, LAST_WEEK, "10:15", "11:45"));

        record(otherLecturerToken, sessionId, entry(anna, "ABSENT")).andExpect(status().isNotFound());
    }

    @Test
    void studentCannotRecordAttendance() throws Exception {
        String sessionId = idOf(createSession(lecturerToken, LAST_WEEK, "10:15", "11:45"));

        record(annaToken, sessionId, entry(anna, "PRESENT")).andExpect(status().isForbidden());
    }

    @Test
    void requestWithForeignEnrollmentSavesNothing() throws Exception {
        String sessionId = idOf(createSession(lecturerToken, LAST_WEEK, "10:15", "11:45"));

        record(lecturerToken, sessionId, entry(anna, "PRESENT"), entry(olaInOtherGroup, "ABSENT"))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/lecturer/sessions/{id}/attendance", sessionId)
                        .header(AUTHORIZATION, bearer(lecturerToken)))
                .andExpect(jsonPath("$.length()").value(0));
    }

    // pomocnicze

    private ResultActions createSession(String token, LocalDate date, String start, String end) throws Exception {
        return mvc.perform(post("/api/lecturer/groups/{id}/sessions", group.getId())
                .header(AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"date": "%s", "startTime": "%s", "endTime": "%s"}
                        """.formatted(date, start, end)));
    }

    private ResultActions record(String token, String sessionId, String... entries) throws Exception {
        return mvc.perform(put("/api/lecturer/sessions/{id}/attendance", sessionId)
                .header(AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"entries": [%s]}
                        """.formatted(String.join(",", entries))));
    }

    private static String entry(GroupEnrollment enrollment, String status) {
        return """
                {"enrollmentId": "%s", "status": "%s"}
                """.formatted(enrollment.getId(), status);
    }
}