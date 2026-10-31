package pl.tokstudiow.api.university;

import jakarta.persistence.*;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

@Entity
@Table(name = "rooms")
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 50)
    private String building;

    @Column(nullable = false, length = 20)
    private String number;

    @Column
    private @Nullable Short capacity;

    protected Room() {
        // wymagany przez JPA
    }

    public Room(String building, String number, @Nullable Short capacity) {
        this.building = building;
        this.number = number;
        this.capacity = capacity;
    }

    public UUID getId() { return id; }
    public String getBuilding() { return building; }
    public String getNumber() { return number; }
    public @Nullable Short getCapacity() { return capacity; }
}