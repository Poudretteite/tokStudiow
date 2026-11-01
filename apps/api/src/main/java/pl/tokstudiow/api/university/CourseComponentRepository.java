package pl.tokstudiow.api.university;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CourseComponentRepository extends JpaRepository<CourseComponent, UUID> {
}