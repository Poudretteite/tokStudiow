package pl.tokstudiow.api.people;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class LecturerService {

    private final LecturerProfileRepository lecturers;
    private final CurrentDeanOffice deanOffice;

    public LecturerService(LecturerProfileRepository lecturers, CurrentDeanOffice deanOffice) {
        this.lecturers = lecturers;
        this.deanOffice = deanOffice;
    }

    /** Aktywni prowadzący z wydziału pracownika dziekanatu. */
    @Transactional(readOnly = true)
    public List<LecturerResponse> listForMyFaculty() {
        return lecturers.findActiveForFaculty(deanOffice.facultyId()).stream().map(LecturerResponse::from).toList();
    }
}
