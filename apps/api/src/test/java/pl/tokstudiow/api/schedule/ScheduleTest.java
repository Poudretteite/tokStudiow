package pl.tokstudiow.api.schedule;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import pl.tokstudiow.api.IntegrationTest;
import pl.tokstudiow.api.groups.ClassGroup;
import pl.tokstudiow.api.studies.Study;
import pl.tokstudiow.api.university.AcademicTerm;
import pl.tokstudiow.api.university.ComponentType;
import pl.tokstudiow.api.university.CourseComponent;
import pl.tokstudiow.api.university.Faculty;
import pl.tokstudiow.api.university.FieldOfStudy;
import pl.tokstudiow.api.university.GradingScaleType;
import pl.tokstudiow.api.university.Room;
import pl.tokstudiow.api.user.Role;
import pl.tokstudiow.api.user.User;

import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ScheduleTest extends IntegrationTest {

    private AcademicTerm term;
    private Room a101;
    private Room a102;
    private ClassGroup lab1;        // prowadzący Piotr, Anna zapisana
    private ClassGroup lab2;        // prowadzący Piotr, nikt nie zapisany
    private ClassGroup lecture;     // prowadząca Ewa, Anna i Jan zapisani
    private ClassGroup seminar;     // prowadząca Ewa, Jan zapisany
    private String deanToken;

    @BeforeEach
    void setUp() throws Exception {
        Faculty science = fixtures.faculty("Wydział Nauk Ścisłych i Technicznych", "WNŚiT");
        FieldOfStudy computerScience = fixtures.fieldOfStudy(science, "Informatyka");
        CourseComponent labs = fixtures.courseComponent(computerScience, "INF-1", ComponentType.LAB, GradingScaleType.NUMERIC);
        CourseComponent lectures = fixtures.courseComponent(computerScience, "INF-2", ComponentType.LECTURE,
                GradingScaleType.NUMERIC);
        CourseComponent seminars = fixtures.courseComponent(computerScience, "INF-3", ComponentType.SEMINAR,
                GradingScaleType.NUMERIC);

        term = fixtures.term();
        jdbc.sql("UPDATE academic_terms SET is_current = TRUE WHERE id = :id").param("id", term.getId()).update();
        a101 = fixtures.room("A", "101", 40);
        a102 = fixtures.room("A", "102", null);

        User piotr = fixtures.lecturer("piotr@test.pl", science);
        User ewa = fixtures.lecturer("ewa@test.pl", science);
        fixtures.deanOffice("dziekanat@test.pl", science);
        Study anna = fixtures.student("anna@test.pl", "1001", computerScience);
        Study jan = fixtures.student("jan@test.pl", "1002", computerScience);

        lab1 = fixtures.group(labs, term, piotr, 1);
        lab2 = fixtures.group(labs, term, piotr, 2);
        lecture = fixtures.group(lectures, term, ewa, 1);
        seminar = fixtures.group(seminars, term, ewa, 2);
        fixtures.enroll(lab1, anna);
        fixtures.enroll(lecture, anna);
        fixtures.enroll(lecture, jan);
        fixtures.enroll(seminar, jan);

        deanToken = tokenFor("dziekanat@test.pl");
    }

    // układanie planu

    @Test
    void deanOfficeAddsEntryThatStudentsAndLecturerSee() throws Exception {
        add(lab1, 1, "08:00", "09:30", "WEEKLY", a101)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.courseCode").value("INF-1"))
                .andExpect(jsonPath("$.roomLabel").value("A, s. 101"))
                .andExpect(jsonPath("$.lecturerName").value("dr Prowadzący piotr@test.pl"))
                .andExpect(jsonPath("$.startTime").value(startsWith("08:00")));

        mySchedule("anna@test.pl")
                .andExpect(jsonPath("$.term.id").value(term.getId().toString()))
                .andExpect(jsonPath("$.entries", hasSize(1)))
                .andExpect(jsonPath("$.entries[0].dayOfWeek").value(1));
        mySchedule("jan@test.pl").andExpect(jsonPath("$.entries", hasSize(0)));
        mvc.perform(get("/api/lecturer/schedule").header(AUTHORIZATION, bearer(tokenFor("piotr@test.pl"))))
                .andExpect(jsonPath("$.entries[0].groupId").value(lab1.getId().toString()));
    }

    @Test
    void roomCannotBeBookedTwiceButOddAndEvenWeeksShareIt() throws Exception {
        add(lecture, 2, "10:00", "11:30", "ODD_WEEKS", a101).andExpect(status().isCreated());

        add(lab2, 2, "11:00", "12:30", "WEEKLY", a101)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Schedule conflict"))
                .andExpect(jsonPath("$.conflicts[*].type", contains("ROOM")))
                .andExpect(jsonPath("$.conflicts[0].entry.courseCode").value("INF-2"));
        add(lab2, 2, "10:00", "11:30", "EVEN_WEEKS", a101).andExpect(status().isCreated());
    }

    @Test
    void lecturerCannotTeachTwoGroupsAtOnce() throws Exception {
        add(lab1, 3, "08:00", "09:30", "WEEKLY", a101).andExpect(status().isCreated());

        add(lab2, 3, "09:00", "10:30", "WEEKLY", a102)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.conflicts[*].type", contains("LECTURER")));
    }

    @Test
    void studentsCannotHaveTwoClassesAtOnceAndConflictSaysHowMany() throws Exception {
        add(lecture, 4, "08:00", "09:30", "WEEKLY", a101).andExpect(status().isCreated());

        // Anna jest w obu grupach; inna sala i inny prowadzący, ale ten sam czas
        add(lab1, 4, "08:30", "10:00", "WEEKLY", a102)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.conflicts[*].type", contains("STUDENTS")))
                .andExpect(jsonPath("$.conflicts[0].studentCount").value(1));
    }

    @Test
    void allConflictsAreReportedAtOnce() throws Exception {
        add(lecture, 5, "12:00", "13:30", "WEEKLY", a101).andExpect(status().isCreated());

        // ta sama prowadząca, ta sama sala i Jan w obu grupach
        add(seminar, 5, "12:00", "13:30", "WEEKLY", a101)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.conflicts[*].type", contains("ROOM", "LECTURER", "STUDENTS")));
    }

    @Test
    void groupCannotHaveOverlappingEntriesButBackToBackIsFine() throws Exception {
        add(lab1, 1, "08:00", "09:30", "WEEKLY", a101).andExpect(status().isCreated());

        add(lab1, 1, "09:00", "10:00", "WEEKLY", a102)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.conflicts[*].type", contains("GROUP")));
        add(lab1, 1, "09:30", "11:00", "WEEKLY", a101).andExpect(status().isCreated());
    }

    @Test
    void invalidTimesAndTooSmallRoomAreRejected() throws Exception {
        add(lab1, 1, "09:30", "08:00", "WEEKLY", a101).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Classes must end after they start"));
        add(lab1, 1, "06:00", "07:30", "WEEKLY", a101).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Classes must fit between 7:00 and 21:00"));
        add(lab1, 8, "08:00", "09:30", "WEEKLY", a101).andExpect(status().isBadRequest());

        Room small = fixtures.room("B", "7", 12);    // grupa ma 30 miejsc
        add(lab1, 1, "08:00", "09:30", "WEEKLY", small).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Room is too small for this group"));
    }

    @Test
    void deanOfficeRemovesEntryAndChangeIsAudited() throws Exception {
        String id = idOf(add(lab1, 1, "08:00", "09:30", "WEEKLY", a101));

        mvc.perform(delete("/api/dean-office/schedule-entries/{id}", id).header(AUTHORIZATION, bearer(deanToken)))
                .andExpect(status().isNoContent());

        groupSchedule(deanToken, lab1).andExpect(jsonPath("$", hasSize(0)));
        createUser("Ada", "Admin", "admin@test.pl", Role.ADMIN);
        auditHistory("schedule_entry", id, tokenFor("admin@test.pl"))
                .andExpect(jsonPath("$[*].action", contains("DELETE", "CREATE")));
    }

    // uprawnienia

    @Test
    void otherFacultyAndOtherRolesCannotChangeSchedule() throws Exception {
        fixtures.deanOffice("dziekanat.wm@test.pl", fixtures.faculty("Wydział Medyczny", "WM"));
        String foreignDean = tokenFor("dziekanat.wm@test.pl");
        String id = idOf(add(lab1, 1, "08:00", "09:30", "WEEKLY", a101));

        mvc.perform(post("/api/dean-office/groups/{id}/schedule", lab1.getId())
                        .header(AUTHORIZATION, bearer(foreignDean))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(entryJson(2, "08:00", "09:30", "WEEKLY", a101)))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/dean-office/schedule-entries/{id}", id).header(AUTHORIZATION, bearer(foreignDean)))
                .andExpect(status().isNotFound());
        groupSchedule(foreignDean, lab1).andExpect(status().isNotFound());

        mvc.perform(post("/api/dean-office/groups/{id}/schedule", lab1.getId())
                        .header(AUTHORIZATION, bearer(tokenFor("piotr@test.pl")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(entryJson(2, "08:00", "09:30", "WEEKLY", a101)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/me/schedule").header(AUTHORIZATION, bearer(tokenFor("piotr@test.pl"))))
                .andExpect(status().isForbidden());
    }

    // semestr

    @Test
    void scheduleOfChosenTermAndEmptyWithoutCurrentTerm() throws Exception {
        add(lab1, 1, "08:00", "09:30", "WEEKLY", a101).andExpect(status().isCreated());
        jdbc.sql("UPDATE academic_terms SET is_current = FALSE").update();

        mySchedule("anna@test.pl")
                .andExpect(jsonPath("$.term").doesNotExist())
                .andExpect(jsonPath("$.entries", hasSize(0)));
        mvc.perform(get("/api/me/schedule").param("termId", term.getId().toString())
                        .header(AUTHORIZATION, bearer(tokenFor("anna@test.pl"))))
                .andExpect(jsonPath("$.entries", hasSize(1)));
        mvc.perform(get("/api/me/schedule").param("termId", UUID.randomUUID().toString())
                        .header(AUTHORIZATION, bearer(tokenFor("anna@test.pl"))))
                .andExpect(status().isNotFound());
    }

    // pomocnicze

    private ResultActions add(ClassGroup group, int day, String start, String end, String frequency, Room room)
            throws Exception {
        return mvc.perform(post("/api/dean-office/groups/{id}/schedule", group.getId())
                .header(AUTHORIZATION, bearer(deanToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(entryJson(day, start, end, frequency, room)));
    }

    private static String entryJson(int day, String start, String end, String frequency, Room room) {
        return """
                {"dayOfWeek": %d, "startTime": "%s", "endTime": "%s", "frequency": "%s", "roomId": "%s"}
                """.formatted(day, start, end, frequency, room.getId());
    }

    private ResultActions mySchedule(String email) throws Exception {
        return mvc.perform(get("/api/me/schedule").header(AUTHORIZATION, bearer(tokenFor(email))));
    }

    private ResultActions groupSchedule(String token, ClassGroup group) throws Exception {
        return mvc.perform(get("/api/dean-office/groups/{id}/schedule", group.getId())
                .header(AUTHORIZATION, bearer(token)));
    }
}
