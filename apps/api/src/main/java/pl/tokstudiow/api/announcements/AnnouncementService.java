package pl.tokstudiow.api.announcements;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tokstudiow.api.audit.AuditAction;
import pl.tokstudiow.api.audit.AuditService;
import pl.tokstudiow.api.common.BusinessRuleException;
import pl.tokstudiow.api.common.CurrentUser;
import pl.tokstudiow.api.common.NotFoundException;
import pl.tokstudiow.api.groups.ClassGroup;
import pl.tokstudiow.api.groups.ClassGroupRepository;
import pl.tokstudiow.api.people.CurrentDeanOffice;
import pl.tokstudiow.api.people.LecturerProfileRepository;
import pl.tokstudiow.api.university.AcademicTerm;
import pl.tokstudiow.api.university.AcademicTermRepository;
import pl.tokstudiow.api.university.Faculty;
import pl.tokstudiow.api.university.FacultyRepository;
import pl.tokstudiow.api.university.FieldOfStudy;
import pl.tokstudiow.api.university.FieldOfStudyRepository;
import pl.tokstudiow.api.user.Role;
import pl.tokstudiow.api.user.User;
import pl.tokstudiow.api.user.UserRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ogłoszenia. Kto do kogo może pisać:
 * <ul>
 *   <li>prowadzący – do swoich grup;</li>
 *   <li>dziekanat – do grup, kierunków i całego swojego wydziału;</li>
 *   <li>administrator – do całej uczelni.</li>
 * </ul>
 * Poprawić ogłoszenie może tylko autor, usunąć – autor albo administrator. Obce lub niewidoczne
 * ogłoszenie daje 404, tak jak inne zasoby spoza uprawnień.
 */
@Service
public class AnnouncementService {

    private static final String AUDIT_ENTITY = "announcement";
    private static final Pageable FEED = PageRequest.of(0, 50);

    private final AnnouncementRepository announcements;
    private final UserRepository users;
    private final ClassGroupRepository groups;
    private final FieldOfStudyRepository fields;
    private final FacultyRepository faculties;
    private final AcademicTermRepository terms;
    private final LecturerProfileRepository lecturers;
    private final CurrentDeanOffice deanOffice;
    private final AuditService audit;

    public AnnouncementService(AnnouncementRepository announcements,
                               UserRepository users,
                               ClassGroupRepository groups,
                               FieldOfStudyRepository fields,
                               FacultyRepository faculties,
                               AcademicTermRepository terms,
                               LecturerProfileRepository lecturers,
                               CurrentDeanOffice deanOffice,
                               AuditService audit) {
        this.announcements = announcements;
        this.users = users;
        this.groups = groups;
        this.fields = fields;
        this.faculties = faculties;
        this.terms = terms;
        this.lecturers = lecturers;
        this.deanOffice = deanOffice;
        this.audit = audit;
    }

    /** Najnowsze 50 ogłoszeń, które dotyczą zalogowanego użytkownika. */
    @Transactional(readOnly = true)
    public List<AnnouncementResponse> feed() {
        UUID userId = CurrentUser.id();
        Role role = CurrentUser.role();
        List<Announcement> list = switch (role) {
            case STUDENT -> announcements.findForStudent(userId, FEED);
            case LECTURER -> announcements.findForLecturer(userId, lecturerFacultyId(userId), FEED);
            case DEAN_OFFICE -> announcements.findForDeanOffice(userId, deanOffice.facultyId(), FEED);
            case ADMIN -> announcements.findAllForAdmin(FEED);
        };
        return list.stream().map(a -> toResponse(a, userId, role)).toList();
    }

    /** Odbiorcy, do których zalogowany może napisać – lista do wyboru w formularzu. */
    @Transactional(readOnly = true)
    public List<AnnouncementTarget> targets() {
        UUID userId = CurrentUser.id();
        UUID currentTermId = terms.findByCurrentTermTrue().map(AcademicTerm::getId).orElse(null);
        List<AnnouncementTarget> targets = new ArrayList<>();
        switch (CurrentUser.role()) {
            case STUDENT -> { }
            case LECTURER -> groups.findForLecturer(userId).stream()
                    .filter(g -> currentTermId == null || g.getAcademicTerm().getId().equals(currentTermId))
                    .map(AnnouncementTarget::of)
                    .forEach(targets::add);
            case DEAN_OFFICE -> {
                UUID facultyId = deanOffice.facultyId();
                targets.add(AnnouncementTarget.of(faculties.findById(facultyId).orElseThrow()));
                fields.findByFacultyIdOrderByNameAscLevelAscModeAsc(facultyId).stream()
                        .map(AnnouncementTarget::of)
                        .forEach(targets::add);
                if (currentTermId != null) {
                    groups.findForFaculty(facultyId, currentTermId).stream()
                            .map(AnnouncementTarget::of)
                            .forEach(targets::add);
                }
            }
            case ADMIN -> targets.add(AnnouncementTarget.everyone());
        }
        return targets;
    }

    @Transactional
    public AnnouncementResponse create(AnnouncementRequest request) {
        UUID userId = CurrentUser.id();
        Role role = CurrentUser.role();
        User author = users.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
        String title = request.title().trim();
        String content = request.content().strip();

        Announcement announcement = switch (request.targetType()) {
            case GROUP -> Announcement.forGroup(author, title, content, groupFor(request.targetId(), userId, role));
            case FIELD_OF_STUDY -> Announcement.forFieldOfStudy(author, title, content, fieldFor(request.targetId(), role));
            case FACULTY -> Announcement.forFaculty(author, title, content, facultyFor(request.targetId(), role));
            case ALL -> {
                requireRole(role, Role.ADMIN);
                if (request.targetId() != null) {
                    throw new BusinessRuleException("Announcement for everyone has no target");
                }
                yield Announcement.forEveryone(author, title, content);
            }
        };
        announcement = announcements.save(announcement);
        audit.record(AUDIT_ENTITY, announcement.getId(), AuditAction.CREATE, null, snapshot(announcement));
        return toResponse(announcement, userId, role);
    }

    @Transactional
    public AnnouncementResponse edit(UUID id, AnnouncementEditRequest request) {
        UUID userId = CurrentUser.id();
        Announcement announcement = announcements.findById(id)
                .filter(a -> a.getAuthor().getId().equals(userId))
                .orElseThrow(AnnouncementService::notFound);
        Map<String, Object> before = snapshot(announcement);
        announcement.edit(request.title().trim(), request.content().strip());
        announcements.flush();
        audit.record(AUDIT_ENTITY, id, AuditAction.UPDATE, before, snapshot(announcement));
        return toResponse(announcement, userId, CurrentUser.role());
    }

    @Transactional
    public void delete(UUID id) {
        UUID userId = CurrentUser.id();
        boolean admin = CurrentUser.role() == Role.ADMIN;
        Announcement announcement = announcements.findById(id)
                .filter(a -> admin || a.getAuthor().getId().equals(userId))
                .orElseThrow(AnnouncementService::notFound);
        audit.record(AUDIT_ENTITY, id, AuditAction.DELETE, snapshot(announcement), null);
        announcements.delete(announcement);
    }

    // uprawnienia do odbiorców

    private ClassGroup groupFor(UUID groupId, UUID userId, Role role) {
        ClassGroup group = groups.findDetailed(requireTarget(groupId))
                .orElseThrow(() -> new NotFoundException("Group not found"));
        boolean allowed = switch (role) {
            case LECTURER -> group.getLecturer().getUserId().equals(userId);
            case DEAN_OFFICE -> group.facultyId().equals(deanOffice.facultyId());
            default -> false;
        };
        if (!allowed) {
            // obca grupa wygląda tak samo jak nieistniejąca
            throw new NotFoundException("Group not found");
        }
        return group;
    }

    private FieldOfStudy fieldFor(UUID fieldId, Role role) {
        requireRole(role, Role.DEAN_OFFICE);
        return fields.findById(requireTarget(fieldId))
                .filter(f -> f.getFaculty().getId().equals(deanOffice.facultyId()))
                .orElseThrow(() -> new NotFoundException("Field of study not found"));
    }

    private Faculty facultyFor(UUID facultyId, Role role) {
        requireRole(role, Role.DEAN_OFFICE);
        if (!requireTarget(facultyId).equals(deanOffice.facultyId())) {
            throw new NotFoundException("Faculty not found");
        }
        return faculties.findById(facultyId).orElseThrow(() -> new NotFoundException("Faculty not found"));
    }

    private static UUID requireTarget(UUID targetId) {
        if (targetId == null) {
            throw new BusinessRuleException("Choose who the announcement is for");
        }
        return targetId;
    }

    private static void requireRole(Role role, Role required) {
        if (role != required) {
            throw new AccessDeniedException("Not allowed to announce to this audience");
        }
    }

    private UUID lecturerFacultyId(UUID userId) {
        return lecturers.findById(userId)
                .map(p -> p.getFaculty().getId())
                .orElseThrow(() -> new AccessDeniedException("No lecturer profile"));
    }

    // odpowiedzi i audyt

    private static AnnouncementResponse toResponse(Announcement a, UUID userId, Role role) {
        boolean mine = a.getAuthor().getId().equals(userId);
        return new AnnouncementResponse(
                a.getId(),
                a.getTitle(),
                a.getContent(),
                a.getAuthor().getFirstName() + " " + a.getAuthor().getLastName(),
                AnnouncementTarget.of(a),
                a.getCreatedAt(),
                a.getUpdatedAt(),
                mine,
                mine || role == Role.ADMIN);
    }

    private static Map<String, Object> snapshot(Announcement a) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("title", a.getTitle());
        snapshot.put("content", a.getContent());
        snapshot.put("target", a.targetType().name());
        AnnouncementTarget target = AnnouncementTarget.of(a);
        if (target.id() != null) {
            snapshot.put("targetId", target.id().toString());
        }
        return snapshot;
    }

    private static NotFoundException notFound() {
        return new NotFoundException("Announcement not found");
    }
}
