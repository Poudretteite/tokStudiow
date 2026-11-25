package pl.tokstudiow.api.schedule;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tokstudiow.api.audit.AuditAction;
import pl.tokstudiow.api.audit.AuditService;
import pl.tokstudiow.api.common.BusinessRuleException;
import pl.tokstudiow.api.common.CurrentUser;
import pl.tokstudiow.api.common.NotFoundException;
import pl.tokstudiow.api.groups.ClassGroup;
import pl.tokstudiow.api.groups.ClassGroupRepository;
import pl.tokstudiow.api.groups.GroupEnrollmentRepository;
import pl.tokstudiow.api.people.CurrentDeanOffice;
import pl.tokstudiow.api.university.AcademicTerm;
import pl.tokstudiow.api.university.AcademicTermRepository;
import pl.tokstudiow.api.university.Room;
import pl.tokstudiow.api.university.RoomRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Tygodniowy plan zajęć. Dziekanat układa terminy grup swojego wydziału; serwer nie przyjmie
 * terminu, w którym sala, prowadzący albo któryś ze studentów grupy ma już inne zajęcia.
 * Student i prowadzący widzą swój plan na wybrany (domyślnie bieżący) semestr.
 */
@Service
public class ScheduleService {

    private static final String AUDIT_ENTITY = "schedule_entry";

    private final ScheduleEntryRepository entries;
    private final ClassGroupRepository groups;
    private final GroupEnrollmentRepository enrollments;
    private final RoomRepository rooms;
    private final AcademicTermRepository terms;
    private final CurrentDeanOffice deanOffice;
    private final AuditService audit;
    private final JdbcClient jdbc;

    public ScheduleService(ScheduleEntryRepository entries,
                           ClassGroupRepository groups,
                           GroupEnrollmentRepository enrollments,
                           RoomRepository rooms,
                           AcademicTermRepository terms,
                           CurrentDeanOffice deanOffice,
                           AuditService audit,
                           JdbcClient jdbc) {
        this.entries = entries;
        this.groups = groups;
        this.enrollments = enrollments;
        this.rooms = rooms;
        this.terms = terms;
        this.deanOffice = deanOffice;
        this.audit = audit;
        this.jdbc = jdbc;
    }

    // dziekanat

    @Transactional(readOnly = true)
    public List<ScheduleEntryResponse> groupSchedule(UUID groupId) {
        ownGroup(groupId);
        return entries.findForGroup(groupId).stream().map(ScheduleEntryResponse::from).toList();
    }

    @Transactional
    public ScheduleEntryResponse add(UUID groupId, CreateScheduleEntryRequest request) {
        ClassGroup group = ownGroup(groupId);
        Room room = rooms.findById(request.roomId()).orElseThrow(() -> new NotFoundException("Room not found"));
        TimeSlot slot = new TimeSlot(request.dayOfWeek(), request.startTime(), request.endTime(), request.frequency(),
                request.weeks());
        AcademicTerm term = group.getAcademicTerm();
        String problem = slot.problem(TimeSlot.weeksInTerm(term.getStartDate(), term.getEndDate()));
        if (problem != null) {
            throw new BusinessRuleException(problem);
        }

        UUID termId = term.getId();
        lockTerm(termId);

        Map<UUID, Set<UUID>> studiesByGroup = studiesByGroup(termId);
        Set<UUID> ownStudents = studiesByGroup.getOrDefault(groupId, Set.of());
        int expected = group.getCapacity() != null ? Math.max(group.getCapacity(), ownStudents.size()) : ownStudents.size();
        if (room.getCapacity() != null && room.getCapacity() < expected) {
            throw new BusinessRuleException("Room is too small for this group");
        }

        List<ScheduleConflict> conflicts = conflicts(group, room, slot, entries.findForTerm(termId), studiesByGroup);
        if (!conflicts.isEmpty()) {
            throw new ScheduleConflictException(conflicts);
        }

        ScheduleEntry entry = entries.save(new ScheduleEntry(group, room, slot));
        audit.record(AUDIT_ENTITY, entry.getId(), AuditAction.CREATE, null, snapshot(entry));
        return ScheduleEntryResponse.from(entry);
    }

    @Transactional
    public void remove(UUID entryId) {
        ScheduleEntry entry = entries.findDetailed(entryId)
                .filter(e -> e.getClassGroup().facultyId().equals(deanOffice.facultyId()))
                .orElseThrow(() -> new NotFoundException("Schedule entry not found"));
        audit.record(AUDIT_ENTITY, entry.getId(), AuditAction.DELETE, snapshot(entry), null);
        entries.delete(entry);
    }

    /**
     * Czy terminy tej grupy nakładają się z zajęciami studenta na tym samym kierunku (te same studia) –
     * sprawdzane przy zapisie do grupy, żeby kolizji nie dało się wprowadzić „tylnymi drzwiami”.
     * Zajęcia na drugim kierunku nie blokują zapisu: student sam godzi dwa kierunki.
     * Wywołujący trzyma już {@link #lockTerm}, więc zapis i nowy termin nie miną się.
     */
    @Transactional(readOnly = true)
    public boolean clashesWithStudyPlan(ClassGroup group, UUID studyId) {
        List<TimeSlot> groupSlots = entries.findForGroup(group.getId()).stream().map(ScheduleEntry::slot).toList();
        if (groupSlots.isEmpty()) {
            return false;
        }
        return entries.findForStudy(group.getAcademicTerm().getId(), studyId).stream()
                .filter(e -> !e.getClassGroup().getId().equals(group.getId()))
                .anyMatch(e -> groupSlots.stream().anyMatch(slot -> slot.overlaps(e.slot())));
    }

    // student i prowadzący

    @Transactional(readOnly = true)
    public ScheduleResponse forStudent(@Nullable UUID termId) {
        return scheduleFor(termId, term -> entries.findForStudent(term.getId(), CurrentUser.id()));
    }

    @Transactional(readOnly = true)
    public ScheduleResponse forLecturer(@Nullable UUID termId) {
        return scheduleFor(termId, term -> entries.findForLecturer(term.getId(), CurrentUser.id()));
    }

    // pomocnicze

    /**
     * Wszystkie kolizje nowego terminu z planem semestru. Studentów porównujemy po studiach
     * (student na konkretnym kierunku): oddzielne grupy, np. laboratoryjna 1 i 2, mogą mieć zajęcia
     * w tym samym czasie, a student dwóch kierunków nie blokuje planu żadnego z nich.
     */
    static List<ScheduleConflict> conflicts(ClassGroup group, Room room, TimeSlot slot, List<ScheduleEntry> termEntries,
                                            Map<UUID, Set<UUID>> studiesByGroup) {
        Set<UUID> ownStudents = studiesByGroup.getOrDefault(group.getId(), Set.of());
        UUID lecturerId = group.getLecturer().getUserId();
        List<ScheduleConflict> found = new ArrayList<>();
        for (ScheduleEntry other : termEntries) {
            if (!slot.overlaps(other.slot())) {
                continue;
            }
            ScheduleEntryResponse described = ScheduleEntryResponse.from(other);
            ClassGroup otherGroup = other.getClassGroup();
            if (otherGroup.getId().equals(group.getId())) {
                found.add(new ScheduleConflict(ScheduleConflict.Type.GROUP, described, null));
                continue;
            }
            if (other.getRoom().getId().equals(room.getId())) {
                found.add(new ScheduleConflict(ScheduleConflict.Type.ROOM, described, null));
            }
            if (otherGroup.getLecturer().getUserId().equals(lecturerId)) {
                found.add(new ScheduleConflict(ScheduleConflict.Type.LECTURER, described, null));
            }
            Set<UUID> shared = new HashSet<>(studiesByGroup.getOrDefault(otherGroup.getId(), Set.of()));
            shared.retainAll(ownStudents);
            if (!shared.isEmpty()) {
                found.add(new ScheduleConflict(ScheduleConflict.Type.STUDENTS, described, shared.size()));
            }
        }
        return found;
    }

    /** Zmiany planu i zapisy w jednym semestrze wykonują się po kolei (blokada do końca transakcji). */
    @Transactional
    public void lockTerm(UUID termId) {
        jdbc.sql("SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))) AS lock")
                .param("key", "schedule:" + termId)
                .query(Integer.class)
                .single();
    }

    private Map<UUID, Set<UUID>> studiesByGroup(UUID termId) {
        Map<UUID, Set<UUID>> result = new HashMap<>();
        for (Object[] row : enrollments.findGroupStudiesInTerm(termId)) {
            result.computeIfAbsent((UUID) row[0], id -> new HashSet<>()).add((UUID) row[1]);
        }
        return result;
    }

    private ClassGroup ownGroup(UUID groupId) {
        UUID facultyId = deanOffice.facultyId();
        return groups.findDetailed(groupId)
                .filter(g -> g.facultyId().equals(facultyId))
                .orElseThrow(() -> new NotFoundException("Group not found"));
    }

    private ScheduleResponse scheduleFor(@Nullable UUID termId, Function<AcademicTerm, List<ScheduleEntry>> query) {
        AcademicTerm term = termId != null
                ? terms.findById(termId).orElseThrow(() -> new NotFoundException("Academic term not found"))
                : terms.findByCurrentTermTrue().orElse(null);
        if (term == null) {
            return new ScheduleResponse(null, List.of());
        }
        return new ScheduleResponse(ScheduleResponse.Term.from(term),
                query.apply(term).stream().map(ScheduleEntryResponse::from).toList());
    }

    private static Map<String, Object> snapshot(ScheduleEntry entry) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("groupId", entry.getClassGroup().getId().toString());
        snapshot.put("roomId", entry.getRoom().getId().toString());
        snapshot.put("dayOfWeek", (int) entry.getDayOfWeek());
        snapshot.put("startTime", entry.getStartTime().toString());
        snapshot.put("endTime", entry.getEndTime().toString());
        snapshot.put("frequency", entry.getFrequency().name());
        if (!entry.getWeeks().isEmpty()) {
            snapshot.put("weeks", entry.getWeeks());
        }
        return snapshot;
    }
}
