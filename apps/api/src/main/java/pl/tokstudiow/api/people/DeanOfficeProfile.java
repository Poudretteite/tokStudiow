package pl.tokstudiow.api.people;

import jakarta.persistence.*;
import pl.tokstudiow.api.university.Faculty;
import pl.tokstudiow.api.user.User;

import java.util.UUID;

@Entity
@Table(name = "dean_office_profiles")
public class DeanOfficeProfile {

    @Id
    private UUID userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "faculty_id", nullable = false)
    private Faculty faculty;

    protected DeanOfficeProfile() {
        // wymagany przez JPA
    }

    public DeanOfficeProfile(User user, Faculty faculty) {
        this.user = user;
        this.faculty = faculty;
    }

    public UUID getUserId() { return userId; }
    public User getUser() { return user; }
    public Faculty getFaculty() { return faculty; }
}