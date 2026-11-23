package pl.tokstudiow.api.university;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "academic_terms")
public class AcademicTerm {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TermType type;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "exam_session_end", nullable = false)
    private LocalDate examSessionEnd;

    @Column(name = "retake_session_end", nullable = false)
    private LocalDate retakeSessionEnd;

    @Column(name = "is_current", nullable = false)
    private boolean currentTerm;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AcademicTerm() {
        // wymagany przez JPA
    }

    /** Sesja i sesja poprawkowa kończą się razem z semestrem. */
    public AcademicTerm(String name, TermType type, LocalDate startDate, LocalDate endDate) {
        this(name, type, startDate, endDate, endDate, endDate);
    }

    /** Sesja poprawkowa kończy się razem z sesją (brak osobnej poprawkowej). */
    public AcademicTerm(String name, TermType type, LocalDate startDate, LocalDate endDate, LocalDate examSessionEnd) {
        this(name, type, startDate, endDate, examSessionEnd, examSessionEnd);
    }

    public AcademicTerm(String name, TermType type, LocalDate startDate, LocalDate endDate,
                        LocalDate examSessionEnd, LocalDate retakeSessionEnd) {
        this.name = name;
        this.type = type;
        this.startDate = startDate;
        this.endDate = endDate;
        this.examSessionEnd = examSessionEnd;
        this.retakeSessionEnd = retakeSessionEnd;
    }

    /** Ile dni po końcu sesji prowadzący może jeszcze sam poprawić pomyłkę w ocenie. */
    public static final int LECTURER_CORRECTION_GRACE_DAYS = 2;

    /** Ostatni dzień korekt ocen z I terminu i wpisanych do końca sesji: koniec sesji + 2 dni. */
    public LocalDate correctionDeadline() {
        return examSessionEnd.plusDays(LECTURER_CORRECTION_GRACE_DAYS);
    }

    /** Ostatni dzień korekt ocen z poprawki i wpisanych w sesji poprawkowej: jej koniec + 2 dni. */
    public LocalDate retakeCorrectionDeadline() {
        return retakeSessionEnd.plusDays(LECTURER_CORRECTION_GRACE_DAYS);
    }

    /** Ocena wpisana po zakończeniu sesji podstawowej należy już do sesji poprawkowej. */
    public boolean isRetakePeriod(LocalDate day) {
        return day.isAfter(examSessionEnd);
    }

    /**
     * Czy prowadzący może w danym dniu sam poprawić pomyłkę (ostatni dzień terminu się liczy).
     *
     * @param retake ocena z poprawki albo wpisana w sesji poprawkowej
     */
    public boolean allowsLecturerCorrection(LocalDate today, boolean retake) {
        return !today.isAfter(retake ? retakeCorrectionDeadline() : correctionDeadline());
    }

    void markCurrent() {
        this.currentTerm = true;
    }

    void unmarkCurrent() {
        this.currentTerm = false;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public TermType getType() { return type; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public LocalDate getExamSessionEnd() { return examSessionEnd; }
    public LocalDate getRetakeSessionEnd() { return retakeSessionEnd; }
    public boolean isCurrentTerm() { return currentTerm; }
}
