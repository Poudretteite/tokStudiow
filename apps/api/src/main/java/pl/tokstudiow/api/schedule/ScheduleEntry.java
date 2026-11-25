package pl.tokstudiow.api.schedule;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import pl.tokstudiow.api.groups.ClassGroup;
import pl.tokstudiow.api.university.Room;

import java.time.Instant;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/** Stały termin zajęć grupy w tygodniowym planie (tabela z V4). */
@Entity
@Table(name = "schedule_entries")
public class ScheduleEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "class_group_id", nullable = false)
    private ClassGroup classGroup;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(name = "day_of_week", nullable = false)
    private short dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private WeekFrequency frequency;

    /** Tylko dla wybranych tygodni: numery tygodni semestru, np. „1,3,4,9”. */
    @Column(name = "selected_weeks", length = 200)
    private String selectedWeeks;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ScheduleEntry() {
        // wymagany przez JPA
    }

    public ScheduleEntry(ClassGroup classGroup, Room room, TimeSlot slot) {
        this.classGroup = classGroup;
        this.room = room;
        this.dayOfWeek = (short) slot.dayOfWeek();
        this.startTime = slot.start();
        this.endTime = slot.end();
        this.frequency = slot.frequency();
        this.selectedWeeks = slot.frequency() == WeekFrequency.SELECTED_WEEKS
                ? slot.weeks().stream().map(String::valueOf).collect(Collectors.joining(","))
                : null;
    }

    public TimeSlot slot() {
        return new TimeSlot(dayOfWeek, startTime, endTime, frequency, getWeeks());
    }

    public List<Integer> getWeeks() {
        if (selectedWeeks == null || selectedWeeks.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(selectedWeeks.split(",")).map(Integer::valueOf).toList();
    }

    public UUID getId() { return id; }
    public ClassGroup getClassGroup() { return classGroup; }
    public Room getRoom() { return room; }
    public short getDayOfWeek() { return dayOfWeek; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
    public WeekFrequency getFrequency() { return frequency; }
}
