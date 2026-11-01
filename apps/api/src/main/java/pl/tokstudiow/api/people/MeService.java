package pl.tokstudiow.api.people;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tokstudiow.api.common.CurrentUser;
import pl.tokstudiow.api.common.NotFoundException;
import pl.tokstudiow.api.user.User;
import pl.tokstudiow.api.user.UserRepository;

import java.util.UUID;

@Service
public class MeService {

    private final UserRepository users;
    private final StudentProfileRepository studentProfiles;
    private final LecturerProfileRepository lecturerProfiles;
    private final DeanOfficeProfileRepository deanOfficeProfiles;

    public MeService(UserRepository users,
                     StudentProfileRepository studentProfiles,
                     LecturerProfileRepository lecturerProfiles,
                     DeanOfficeProfileRepository deanOfficeProfiles) {
        this.users = users;
        this.studentProfiles = studentProfiles;
        this.lecturerProfiles = lecturerProfiles;
        this.deanOfficeProfiles = deanOfficeProfiles;
    }

    @Transactional(readOnly = true)
    public MeResponse me() {
        UUID id = CurrentUser.id();
        User user = users.findById(id).orElseThrow(() -> new NotFoundException("User not found"));

        String albumNumber = null;
        UUID facultyId = null;
        String academicTitle = null;
        switch (user.getRole()) {
            case STUDENT -> albumNumber = studentProfiles.findById(id)
                    .map(StudentProfile::getAlbumNumber).orElse(null);
            case LECTURER -> {
                var profile = lecturerProfiles.findById(id).orElse(null);
                if (profile != null) {
                    facultyId = profile.getFaculty().getId();
                    academicTitle = profile.getAcademicTitle();
                }
            }
            case DEAN_OFFICE -> facultyId = deanOfficeProfiles.findById(id)
                    .map(p -> p.getFaculty().getId()).orElse(null);
            case ADMIN -> { }
        }

        return new MeResponse(user.getId(), user.getFirstName(), user.getLastName(), user.getEmail(),
                user.getRole(), albumNumber, facultyId, academicTitle);
    }
}