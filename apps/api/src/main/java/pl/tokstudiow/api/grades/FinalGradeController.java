package pl.tokstudiow.api.grades;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
public class FinalGradeController {

    private final FinalGradeService finalGradeService;

    public FinalGradeController(FinalGradeService finalGradeService) {
        this.finalGradeService = finalGradeService;
    }

    @PutMapping("/api/lecturer/groups/{groupId}/final-grades")
    @PreAuthorize("hasRole('LECTURER')")
    public FinalGradeResponse saveDraft(@PathVariable UUID groupId, @Valid @RequestBody FinalGradeRequest request) {
        return finalGradeService.saveDraft(groupId, request);
    }

    @PostMapping("/api/lecturer/groups/{groupId}/final-grades/{finalGradeId}/approve")
    @PreAuthorize("hasRole('LECTURER')")
    public FinalGradeResponse approve(@PathVariable UUID groupId, @PathVariable UUID finalGradeId) {
        return finalGradeService.approve(groupId, finalGradeId);
    }

    @GetMapping("/api/lecturer/groups/{groupId}/final-grades")
    @PreAuthorize("hasRole('LECTURER')")
    public List<FinalGradeResponse> listForGroup(@PathVariable UUID groupId) {
        return finalGradeService.listForGroup(groupId);
    }

    @GetMapping("/api/lecturer/groups/{groupId}/final-grade-proposals")
    @PreAuthorize("hasRole('LECTURER')")
    public List<FinalGradeProposalResponse> proposals(@PathVariable UUID groupId) {
        return finalGradeService.proposals(groupId);
    }

    @GetMapping("/api/me/transcript")
    @PreAuthorize("hasRole('STUDENT')")
    public List<StudyTranscriptResponse> myTranscript() {
        return finalGradeService.myTranscript();
    }
}