package pl.tokstudiow.api.dev;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pl.tokstudiow.api.attendance.*;
import pl.tokstudiow.api.grades.*;
import pl.tokstudiow.api.groups.*;
import pl.tokstudiow.api.people.*;
import pl.tokstudiow.api.studies.Study;
import pl.tokstudiow.api.studies.StudyRepository;
import pl.tokstudiow.api.university.*;
import pl.tokstudiow.api.user.Role;
import pl.tokstudiow.api.user.User;
import pl.tokstudiow.api.user.UserRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

@Component
@Profile("dev")
public class DevDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataInitializer.class);

    private final FacultyRepository faculties;
    private final FieldOfStudyRepository fields;
    private final CourseRepository courses;
    private final GradingScaleRepository scales;
    private final AcademicTermRepository terms;
    private final UserRepository users;
    private final StudentProfileRepository studentProfiles;
    private final LecturerProfileRepository lecturerProfiles;
    private final DeanOfficeProfileRepository deanOfficeProfiles;
    private final StudyRepository studies;
    private final ClassGroupRepository groups;
    private final GroupEnrollmentRepository enrollments;
    private final AssessmentItemRepository items;
    private final PartialGradeRepository partialGrades;
    private final FinalGradeRepository finalGrades;
    private final ClassSessionRepository sessions;
    private final AttendanceRepository attendance;
    private final PasswordEncoder passwordEncoder;
    private final String password;

    public DevDataInitializer(FacultyRepository faculties, FieldOfStudyRepository fields,
                              CourseRepository courses, GradingScaleRepository scales,
                              AcademicTermRepository terms, UserRepository users,
                              StudentProfileRepository studentProfiles,
                              LecturerProfileRepository lecturerProfiles,
                              DeanOfficeProfileRepository deanOfficeProfiles,
                              StudyRepository studies, ClassGroupRepository groups,
                              GroupEnrollmentRepository enrollments, AssessmentItemRepository items,
                              PartialGradeRepository partialGrades, FinalGradeRepository finalGrades,
                              ClassSessionRepository sessions, AttendanceRepository attendance,
                              PasswordEncoder passwordEncoder,
                              @Value("${DEV_ADMIN_PASSWORD}") String password) {
        this.faculties = faculties;
        this.fields = fields;
        this.courses = courses;
        this.scales = scales;
        this.terms = terms;
        this.users = users;
        this.studentProfiles = studentProfiles;
        this.lecturerProfiles = lecturerProfiles;
        this.deanOfficeProfiles = deanOfficeProfiles;
        this.studies = studies;
        this.groups = groups;
        this.enrollments = enrollments;
        this.items = items;
        this.partialGrades = partialGrades;
        this.finalGrades = finalGrades;
        this.sessions = sessions;
        this.attendance = attendance;
        this.passwordEncoder = passwordEncoder;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (faculties.count() > 0) {
            return;
        }
        LocalDate today = LocalDate.now();

        // Struktura uczelni
        Faculty faculty = faculties.save(new Faculty("Wydział Nauk Ścisłych i Technicznych", "WNŚiT"));
        FieldOfStudy cs = fields.save(new FieldOfStudy(
                faculty, "Informatyka", StudyLevel.FIRST_CYCLE, StudyMode.FULL_TIME, (short) 7));
        GradingScale numeric = scale(GradingScaleType.NUMERIC);
        GradingScale passFail = scale(GradingScaleType.PASS_FAIL);

        Course databases = new Course(cs, "INF-BD", "Bazy danych", (short) 5, (short) 3);
        databases.addComponent(ComponentType.LECTURE, (short) 30, passFail);
        databases.addComponent(ComponentType.LAB, (short) 30, numeric);
        courses.save(databases);
        Course systems = new Course(cs, "INF-SO", "Systemy operacyjne", (short) 4, (short) 3);
        systems.addComponent(ComponentType.LAB, (short) 30, numeric);
        courses.save(systems);

        AcademicTerm term = terms.save(new AcademicTerm(
                "Semestr deweloperski", TermType.WINTER, today.minusMonths(1), today.plusMonths(4)));

        // Ludzie
        User lecturerUser = user("Jan", "Kowalski", "prowadzacy@tokstudiow.local", Role.LECTURER);
        LecturerProfile lecturer = lecturerProfiles.save(new LecturerProfile(lecturerUser, faculty, "dr inż."));
        User dean = user("Ewa", "Wiśniewska", "dziekanat@tokstudiow.local", Role.DEAN_OFFICE);
        deanOfficeProfiles.save(new DeanOfficeProfile(dean, faculty));
        User studentUser = user("Anna", "Nowak", "student@tokstudiow.local", Role.STUDENT);
        StudentProfile student = studentProfiles.save(new StudentProfile(studentUser, "123456", null));
        Study study = studies.save(new Study(student, cs, (short) (today.getYear() - 1), (short) 3));

        // Grupy i zapisy
        ClassGroup dbLecture = groups.save(new ClassGroup(
                component(databases, ComponentType.LECTURE), term, lecturer, (short) 1, null));
        ClassGroup dbLab = groups.save(new ClassGroup(
                component(databases, ComponentType.LAB), term, lecturer, (short) 1, (short) 15));
        ClassGroup osLab = groups.save(new ClassGroup(
                component(systems, ComponentType.LAB), term, lecturer, (short) 1, (short) 15));
        GroupEnrollment inDbLecture = enrollments.save(new GroupEnrollment(dbLecture, study));
        GroupEnrollment inDbLab = enrollments.save(new GroupEnrollment(dbLab, study));
        GroupEnrollment inOsLab = enrollments.save(new GroupEnrollment(osLab, study));

        // Oceny cząstkowe
        AssessmentItem quiz = items.save(new AssessmentItem(
                dbLab, "Kolokwium", new BigDecimal("40"), (short) 1, today.minusWeeks(2)));
        AssessmentItem project = items.save(new AssessmentItem(
                dbLab, "Projekt", new BigDecimal("60"), (short) 2, null));
        partialGrades.save(new PartialGrade(inDbLab, quiz, value(numeric, "4,0"), (short) 1, lecturerUser));
        partialGrades.save(new PartialGrade(inDbLab, project, value(numeric, "4,5"), (short) 1, lecturerUser));

        // Oceny końcowe (zatwierdzone), w tym poprawka
        approved(inDbLecture, FinalAttempt.FIRST, value(passFail, "zal"), lecturerUser);
        approved(inDbLab, FinalAttempt.FIRST, value(numeric, "4,5"), lecturerUser);
        approved(inOsLab, FinalAttempt.FIRST, value(numeric, "2,0"), lecturerUser);
        approved(inOsLab, FinalAttempt.SECOND, value(numeric, "3,5"), lecturerUser);

        // Zajęcia i obecności
        for (int week = 1; week <= 3; week++) {
            ClassSession session = sessions.save(new ClassSession(
                    dbLab, null, today.minusWeeks(week), LocalTime.of(10, 15), LocalTime.of(11, 45)));
            AttendanceStatus status = week == 2 ? AttendanceStatus.ABSENT : AttendanceStatus.PRESENT;
            attendance.save(new Attendance(session, inDbLab, status, lecturerUser));
        }

        log.info("Dev data created: student@ / prowadzacy@ / dziekanat@tokstudiow.local (password from .env)");
    }

    private User user(String firstName, String lastName, String email, Role role) {
        return users.save(new User(firstName, lastName, email, passwordEncoder.encode(password), role));
    }

    private GradingScale scale(GradingScaleType type) {
        return scales.findAllWithValues().stream().filter(s -> s.getType() == type).findFirst().orElseThrow();
    }

    private static GradeValue value(GradingScale scale, String label) {
        return scale.getValues().stream().filter(v -> v.getLabel().equals(label)).findFirst().orElseThrow();
    }

    private static CourseComponent component(Course course, ComponentType type) {
        return course.getComponents().stream().filter(c -> c.getType() == type).findFirst().orElseThrow();
    }

    private void approved(GroupEnrollment enrollment, FinalAttempt attempt, GradeValue value, User approver) {
        FinalGrade grade = new FinalGrade(enrollment, attempt, value);
        grade.approve(approver);
        finalGrades.save(grade);
    }
}