package pl.tokstudiow.api.people;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import pl.tokstudiow.api.common.CurrentUser;

import java.util.UUID;

@Component
public class CurrentDeanOffice {

    private final DeanOfficeProfileRepository deanOfficeProfiles;

    public CurrentDeanOffice(DeanOfficeProfileRepository deanOfficeProfiles) {
        this.deanOfficeProfiles = deanOfficeProfiles;
    }

    public UUID facultyId() {
        return deanOfficeProfiles.findById(CurrentUser.id())
                .map(p -> p.getFaculty().getId())
                .orElseThrow(() -> new AccessDeniedException("No dean office profile"));
    }
}