package pl.tokstudiow.api.studies;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tokstudiow.api.audit.AuditAction;
import pl.tokstudiow.api.audit.AuditService;
import pl.tokstudiow.api.common.BusinessRuleException;
import pl.tokstudiow.api.common.NotFoundException;
import pl.tokstudiow.api.common.RowError;
import pl.tokstudiow.api.common.RowValidationException;
import pl.tokstudiow.api.people.CurrentDeanOffice;
import pl.tokstudiow.api.people.StudentProfile;
import pl.tokstudiow.api.people.StudentProfileRepository;
import pl.tokstudiow.api.university.FieldOfStudy;
import pl.tokstudiow.api.university.FieldOfStudyRepository;
import pl.tokstudiow.api.user.InitialPasswordGenerator;
import pl.tokstudiow.api.user.Role;
import pl.tokstudiow.api.user.StudentEmailGenerator;
import pl.tokstudiow.api.user.User;
import pl.tokstudiow.api.user.UserRepository;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Dziekanat zakłada konta studentów hurtowo: konto, profil i przebieg studiów
 * w jednej transakcji. Najpierw sprawdzane są wszystkie wiersze naraz, więc
 * błąd w jednym wierszu odrzuca cały import, a odpowiedź wskazuje każdy problem.
 */
@Service
public class StudentImportService {

    private final UserRepository users;
    private final StudentProfileRepository studentProfiles;
    private final StudyRepository studies;
    private final FieldOfStudyRepository fields;
    private final CurrentDeanOffice deanOffice;
    private final PasswordEncoder passwordEncoder;
    private final InitialPasswordGenerator passwordGenerator;
    private final StudentEmailGenerator emailGenerator;
    private final AuditService audit;

    public StudentImportService(UserRepository users,
                                StudentProfileRepository studentProfiles,
                                StudyRepository studies,
                                FieldOfStudyRepository fields,
                                CurrentDeanOffice deanOffice,
                                PasswordEncoder passwordEncoder,
                                InitialPasswordGenerator passwordGenerator,
                                StudentEmailGenerator emailGenerator,
                                AuditService audit) {
        this.users = users;
        this.studentProfiles = studentProfiles;
        this.studies = studies;
        this.fields = fields;
        this.deanOffice = deanOffice;
        this.passwordEncoder = passwordEncoder;
        this.passwordGenerator = passwordGenerator;
        this.emailGenerator = emailGenerator;
        this.audit = audit;
    }

    @Transactional
    public List<ImportedStudentResponse> importStudents(StudentImportRequest request) {
        UUID facultyId = deanOffice.facultyId();
        FieldOfStudy field = fields.findById(request.fieldOfStudyId())
                .filter(f -> f.getFaculty().getId().equals(facultyId))
                .orElseThrow(() -> new NotFoundException("Field of study not found"));
        if (request.currentSemester() > field.getSemestersCount()) {
            throw new BusinessRuleException("Semester exceeds the length of the field of study");
        }

        List<NormalizedRow> rows = request.students().stream()
                .map(row -> NormalizedRow.of(row, emailGenerator))
                .toList();
        List<RowError> errors = validate(rows);
        if (!errors.isEmpty()) {
            throw new RowValidationException("Some rows cannot be imported", errors);
        }

        // BCrypt jest celowo wolny; równolegle hasła dla 300 osób liczą się w kilka sekund, nie minutę
        List<String> passwords = rows.stream().map(r -> passwordGenerator.generate()).toList();
        List<String> hashes = passwords.parallelStream().map(passwordEncoder::encode).toList();

        List<ImportedStudentResponse> imported = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            NormalizedRow row = rows.get(i);
            User account = new User(row.firstName(), row.lastName(), row.email(), hashes.get(i), Role.STUDENT);
            account.requirePasswordChange();
            User user = users.save(account);
            StudentProfile profile = studentProfiles.save(new StudentProfile(user, row.albumNumber(), null));
            Study study = studies.save(new Study(profile, field, request.startYear(), request.currentSemester()));

            audit.record("user", user.getId(), AuditAction.CREATE, null, userSnapshot(user, row.albumNumber()));
            audit.record("study", study.getId(), AuditAction.CREATE, null, StudyService.snapshot(study));

            imported.add(new ImportedStudentResponse(study.getId(), user.getId(), row.firstName(), row.lastName(),
                    row.email(), row.albumNumber(), passwords.get(i)));
        }
        return imported;
    }

    /**
     * Duplikaty w samym pliku i numery albumów lub adresy, które już są w systemie.
     * Adres powstaje z numeru albumu, więc jego konflikt zgłaszany jest przy albumie.
     */
    private List<RowError> validate(List<NormalizedRow> rows) {
        Set<String> takenEmails = new HashSet<>(users.findExistingEmails(rows.stream().map(NormalizedRow::email).toList()));
        Set<String> takenAlbums = new HashSet<>(
                studentProfiles.findExistingAlbumNumbers(rows.stream().map(NormalizedRow::albumNumber).toList()));

        List<RowError> errors = new ArrayList<>();
        Set<String> seenEmails = new HashSet<>();
        Set<String> seenAlbums = new HashSet<>();
        for (int i = 0; i < rows.size(); i++) {
            NormalizedRow row = rows.get(i);
            int number = i + 1;
            if (!seenAlbums.add(row.albumNumber())) {
                errors.add(new RowError(number, "albumNumber", "Duplicate album number in import"));
            } else if (takenAlbums.contains(row.albumNumber())) {
                errors.add(new RowError(number, "albumNumber", "Album number already in use"));
            } else if (!seenEmails.add(row.email())) {
                errors.add(new RowError(number, "albumNumber", "Duplicate email in import"));
            } else if (takenEmails.contains(row.email())) {
                errors.add(new RowError(number, "albumNumber", "Email already in use"));
            }
        }
        return errors;
    }

    /** Pola zapisywane w dzienniku – jawna lista, nigdy hasło ani jego skrót. */
    private static Map<String, Object> userSnapshot(User user, String albumNumber) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("firstName", user.getFirstName());
        snapshot.put("lastName", user.getLastName());
        snapshot.put("email", user.getEmail());
        snapshot.put("role", user.getRole().name());
        snapshot.put("active", user.isActive());
        snapshot.put("albumNumber", albumNumber);
        return snapshot;
    }

    /** Dane po oczyszczeniu: bez spacji na brzegach, z wygenerowanym adresem uczelnianym. */
    record NormalizedRow(String firstName, String lastName, String email, String albumNumber) {

        static NormalizedRow of(StudentImportRequest.Row row, StudentEmailGenerator emails) {
            String firstName = row.firstName().trim();
            String lastName = row.lastName().trim();
            String album = row.albumNumber().trim();
            return new NormalizedRow(firstName, lastName, emails.generate(firstName, lastName, album), album);
        }
    }
}
