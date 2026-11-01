package pl.tokstudiow.api.people;

import jakarta.persistence.*;
import org.jspecify.annotations.Nullable;
import pl.tokstudiow.api.user.User;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "student_profiles")
public class StudentProfile {

    @Id
    private UUID userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "album_number", nullable = false, length = 20)
    private String albumNumber;

    @Column
    private @Nullable LocalDate birthdate;

    protected StudentProfile() {
        // wymagany przez JPA
    }

    public StudentProfile(User user, String albumNumber, @Nullable LocalDate birthdate) {
        this.user = user;
        this.albumNumber = albumNumber;
        this.birthdate = birthdate;
    }

    public UUID getUserId() { return userId; }
    public User getUser() { return user; }
    public String getAlbumNumber() { return albumNumber; }
    public @Nullable LocalDate getBirthdate() { return birthdate; }
}