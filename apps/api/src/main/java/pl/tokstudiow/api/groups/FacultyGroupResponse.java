package pl.tokstudiow.api.groups;

/** Grupa na liście dziekanatu razem z liczbą zapisanych studentów. */
public record FacultyGroupResponse(ClassGroupResponse group, long enrolledCount) {
}
