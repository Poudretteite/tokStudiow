package pl.tokstudiow.api.announcements;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.jspecify.annotations.Nullable;
import pl.tokstudiow.api.groups.ClassGroup;
import pl.tokstudiow.api.university.Faculty;
import pl.tokstudiow.api.university.FieldOfStudy;
import pl.tokstudiow.api.user.User;

import java.time.Instant;
import java.util.UUID;

/** Ogłoszenie z jednym odbiorcą: grupą, kierunkiem, wydziałem albo – gdy brak wszystkich – całą uczelnią. */
@Entity
@Table(name = "announcements")
public class Announcement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false, updatable = false)
    private User author;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_group_id", updatable = false)
    private @Nullable ClassGroup classGroup;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "field_of_study_id", updatable = false)
    private @Nullable FieldOfStudy fieldOfStudy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "faculty_id", updatable = false)
    private @Nullable Faculty faculty;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Announcement() {
        // wymagany przez JPA
    }

    private Announcement(User author, String title, String content) {
        this.author = author;
        this.title = title;
        this.content = content;
    }

    static Announcement forGroup(User author, String title, String content, ClassGroup group) {
        Announcement a = new Announcement(author, title, content);
        a.classGroup = group;
        return a;
    }

    static Announcement forFieldOfStudy(User author, String title, String content, FieldOfStudy field) {
        Announcement a = new Announcement(author, title, content);
        a.fieldOfStudy = field;
        return a;
    }

    static Announcement forFaculty(User author, String title, String content, Faculty faculty) {
        Announcement a = new Announcement(author, title, content);
        a.faculty = faculty;
        return a;
    }

    static Announcement forEveryone(User author, String title, String content) {
        return new Announcement(author, title, content);
    }

    /** Odbiorcy się nie zmieniają – poprawić można tylko treść (inaczej ogłoszenie „przeskoczyłoby” do innych osób). */
    void edit(String title, String content) {
        this.title = title;
        this.content = content;
    }

    public TargetType targetType() {
        if (classGroup != null) return TargetType.GROUP;
        if (fieldOfStudy != null) return TargetType.FIELD_OF_STUDY;
        if (faculty != null) return TargetType.FACULTY;
        return TargetType.ALL;
    }

    public UUID getId() { return id; }
    public User getAuthor() { return author; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public @Nullable ClassGroup getClassGroup() { return classGroup; }
    public @Nullable FieldOfStudy getFieldOfStudy() { return fieldOfStudy; }
    public @Nullable Faculty getFaculty() { return faculty; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
