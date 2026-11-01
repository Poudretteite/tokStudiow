package pl.tokstudiow.api.admin;

import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tokstudiow.api.audit.AuditAction;
import pl.tokstudiow.api.audit.AuditService;
import pl.tokstudiow.api.auth.RefreshTokenRepository;
import pl.tokstudiow.api.common.BusinessRuleException;
import pl.tokstudiow.api.common.ConflictException;
import pl.tokstudiow.api.common.NotFoundException;
import pl.tokstudiow.api.people.DeanOfficeProfile;
import pl.tokstudiow.api.people.DeanOfficeProfileRepository;
import pl.tokstudiow.api.people.LecturerProfile;
import pl.tokstudiow.api.people.LecturerProfileRepository;
import pl.tokstudiow.api.people.StudentProfile;
import pl.tokstudiow.api.people.StudentProfileRepository;
import pl.tokstudiow.api.university.Faculty;
import pl.tokstudiow.api.university.FacultyRepository;
import pl.tokstudiow.api.user.User;
import pl.tokstudiow.api.user.UserRepository;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class UserManagementService {

    private static final String AUDIT_ENTITY = "user";

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenRepository refreshTokens;
    private final AuditService audit;
    private final FacultyRepository faculties;
    private final StudentProfileRepository studentProfiles;
    private final LecturerProfileRepository lecturerProfiles;
    private final DeanOfficeProfileRepository deanOfficeProfiles;

    public UserManagementService(UserRepository users,
                                 PasswordEncoder passwordEncoder,
                                 RefreshTokenRepository refreshTokens,
                                 AuditService audit,
                                 FacultyRepository faculties,
                                 StudentProfileRepository studentProfiles,
                                 LecturerProfileRepository lecturerProfiles,
                                 DeanOfficeProfileRepository deanOfficeProfiles) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokens = refreshTokens;
        this.audit = audit;
        this.faculties = faculties;
        this.studentProfiles = studentProfiles;
        this.lecturerProfiles = lecturerProfiles;
        this.deanOfficeProfiles = deanOfficeProfiles;
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        if (users.existsByEmailIgnoreCase(request.email())) {
            throw new ConflictException("Email already in use");
        }

        // 1. Sprawdź wymagania profilu, zanim cokolwiek zapiszesz
        String albumNumber = null;
        Faculty faculty = null;
        switch (request.role()) {
            case STUDENT -> {
                if (request.albumNumber() == null || request.albumNumber().isBlank()) {
                    throw new BusinessRuleException("Album number is required for students");
                }
                albumNumber = request.albumNumber().trim();
                if (studentProfiles.existsByAlbumNumber(albumNumber)) {
                    throw new ConflictException("Album number already in use");
                }
            }
            case LECTURER, DEAN_OFFICE -> {
                if (request.facultyId() == null) {
                    throw new BusinessRuleException("Faculty is required for this role");
                }
                faculty = faculties.findById(request.facultyId())
                        .orElseThrow(() -> new NotFoundException("Faculty not found"));
            }
            case ADMIN -> {
                // administrator nie ma profilu
            }
        }

        // 2. Zapisz konto i profil w jednej transakcji
        User user = users.save(new User(
                request.firstName().trim(),
                request.lastName().trim(),
                request.email(),
                passwordEncoder.encode(request.password()),
                request.role()));

        switch (request.role()) {
            case STUDENT -> studentProfiles.save(new StudentProfile(user, albumNumber, request.birthdate()));
            case LECTURER -> lecturerProfiles.save(new LecturerProfile(user, faculty, request.academicTitle()));
            case DEAN_OFFICE -> deanOfficeProfiles.save(new DeanOfficeProfile(user, faculty));
            case ADMIN -> { }
        }

        UUID facultyId = faculty == null ? null : faculty.getId();
        audit.record(AUDIT_ENTITY, user.getId(), AuditAction.CREATE, null, snapshot(user, albumNumber, facultyId));
        return UserResponse.from(user, albumNumber, facultyId);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> list() {
        Map<UUID, String> albums = new HashMap<>();
        studentProfiles.findAll().forEach(p -> albums.put(p.getUserId(), p.getAlbumNumber()));

        Map<UUID, UUID> userFaculties = new HashMap<>();
        lecturerProfiles.findAll().forEach(p -> userFaculties.put(p.getUserId(), p.getFaculty().getId()));
        deanOfficeProfiles.findAll().forEach(p -> userFaculties.put(p.getUserId(), p.getFaculty().getId()));

        return users.findAll(Sort.by("lastName", "firstName")).stream()
                .map(u -> UserResponse.from(u, albums.get(u.getId()), userFaculties.get(u.getId())))
                .toList();
    }

    @Transactional
    public void deactivate(UUID userId, UUID actingAdminId) {
        if (userId.equals(actingAdminId)) {
            throw new BusinessRuleException("You cannot deactivate your own account");
        }
        User user = users.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        Map<String, Object> before = snapshot(user, null, null);
        user.deactivate();
        refreshTokens.revokeAllActiveForUser(userId, Instant.now());

        audit.record(AUDIT_ENTITY, userId, AuditAction.UPDATE, before, snapshot(user, null, null));
    }

    /** Pola zapisywane w dzienniku – jawna lista, nigdy hash hasła. */
    private static Map<String, Object> snapshot(User user, @Nullable String albumNumber, @Nullable UUID facultyId) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("firstName", user.getFirstName());
        snapshot.put("lastName", user.getLastName());
        snapshot.put("email", user.getEmail());
        snapshot.put("role", user.getRole().name());
        snapshot.put("active", user.isActive());
        if (albumNumber != null) {
            snapshot.put("albumNumber", albumNumber);
        }
        if (facultyId != null) {
            snapshot.put("facultyId", facultyId.toString());
        }
        return snapshot;
    }
}