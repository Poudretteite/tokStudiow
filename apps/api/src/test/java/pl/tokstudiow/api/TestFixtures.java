package pl.tokstudiow.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.security.crypto.password.PasswordEncoder;
import pl.tokstudiow.api.groups.ClassGroup;
import pl.tokstudiow.api.groups.ClassGroupRepository;
import pl.tokstudiow.api.groups.GroupEnrollment;
import pl.tokstudiow.api.groups.GroupEnrollmentRepository;
import pl.tokstudiow.api.people.*;
import pl.tokstudiow.api.studies.Study;
import pl.tokstudiow.api.studies.StudyRepository;
import pl.tokstudiow.api.university.*;
import pl.tokstudiow.api.user.Role;
import pl.tokstudiow.api.user.User;
import pl.tokstudiow.api.user.UserRepository;

import java.time.LocalDate;

/** Tworzy dane testowe bezpośrednio w bazie, z pominięciem API. */
@TestComponent
public class TestFixtures {

    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired FacultyRepository faculties;
    @Autowired FieldOfStudyRepository fields;
    @Autowired CourseRepository courses;
    @Autowired GradingScaleRepository scales;
    @Autowired AcademicTermRepository terms;
    @Autowired StudentProfileRepository studentProfiles;
    @Autowired LecturerProfileRepository lecturerProfiles;
    @Autowired DeanOfficeProfileRepository deanOfficeProfiles;
    @Autowired StudyRepository studies;
    @Autowired ClassGroupRepository groups;
    @Autowired GroupEnrollmentRepository enrollments;

    public Faculty faculty(String name, String shortName) {
        return faculties.save(new Faculty(name, shortName));
    }

    public FieldOfStudy fieldOfStudy(Faculty faculty, String name) {
        return fields.save(new FieldOfStudy(faculty, name, StudyLevel.FIRST_CYCLE, StudyMode.FULL_TIME, (short) 7));
    }

    public CourseComponent courseComponent(FieldOfStudy field, String code, ComponentType type, GradingScaleType scaleType) {
        GradingScale scale = scale(scaleType);
        Course course = new Course(field, code, "Przedmiot " + code, (short) 5, (short) 3);
        course.addComponent(type, (short) 30, scale);
        courses.save(course);
        return course.getComponents().getFirst();
    }

    public GradingScale scale(GradingScaleType type) {
        return scales.findAllWithValues().stream()
                .filter(s -> s.getType() == type)
                .findFirst().orElseThrow();
    }

    public GradeValue gradeValue(GradingScaleType type, String label) {
        return scale(type).getValues().stream()
                .filter(v -> v.getLabel().equals(label))
                .findFirst().orElseThrow();
    }

    public AcademicTerm term() {
        return terms.save(new AcademicTerm("2026/2027 zimowy", TermType.WINTER,
                LocalDate.of(2026, 10, 1), LocalDate.of(2027, 2, 20)));
    }

    public User user(String firstName, String lastName, String email, Role role) {
        return users.save(new User(firstName, lastName, email, passwordEncoder.encode(IntegrationTest.PASSWORD), role));
    }

    public User lecturer(String email, Faculty faculty) {
        User user = user("Prowadzący", email, email, Role.LECTURER);
        lecturerProfiles.save(new LecturerProfile(user, faculty, "dr"));
        return user;
    }

    public User deanOffice(String email, Faculty faculty) {
        User user = user("Dziekanat", email, email, Role.DEAN_OFFICE);
        deanOfficeProfiles.save(new DeanOfficeProfile(user, faculty));
        return user;
    }

    public Study student(String email, String albumNumber, FieldOfStudy field) {
        User user = user("Student", email, email, Role.STUDENT);
        StudentProfile profile = studentProfiles.save(new StudentProfile(user, albumNumber, null));
        return studies.save(new Study(profile, field, (short) 2026, (short) 3));
    }

    public ClassGroup group(CourseComponent component, AcademicTerm term, User lecturer, int number) {
        LecturerProfile profile = lecturerProfiles.findById(lecturer.getId()).orElseThrow();
        return groups.save(new ClassGroup(component, term, profile, (short) number, (short) 30));
    }

    public GroupEnrollment enroll(ClassGroup group, Study study) {
        return enrollments.save(new GroupEnrollment(group, study));
    }
}