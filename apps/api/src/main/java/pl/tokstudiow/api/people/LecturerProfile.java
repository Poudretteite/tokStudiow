package pl.tokstudiow.api.people;

import jakarta.persistence.*;
import org.jspecify.annotations.Nullable;
import pl.tokstudiow.api.university.Faculty;
import pl.tokstudiow.api.user.User;

import java.util.UUID;

@Entity
@Table(name = "lecturer_profiles")
public class LecturerProfile {

    @Id
    private UUID userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "faculty_id", nullable = false)
    private Faculty faculty;

    @Column(name = "academic_title", length = 50)
    private @Nullable String academicTitle;

    @Column(name = "office_room", length = 50)
    private @Nullable String officeRoom;

    @Column(name = "consultation_hours", length = 255)
    private @Nullable String consultationHours;

    protected LecturerProfile() {
        // wymagany przez JPA
    }

    public LecturerProfile(User user, Faculty faculty, @Nullable String academicTitle) {
        this.user = user;
        this.faculty = faculty;
        this.academicTitle = academicTitle;
    }

    public UUID getUserId() { return userId; }
    public User getUser() { return user; }
    public Faculty getFaculty() { return faculty; }
    public @Nullable String getAcademicTitle() { return academicTitle; }
    public @Nullable String getOfficeRoom() { return officeRoom; }
    public @Nullable String getConsultationHours() { return consultationHours; }
}