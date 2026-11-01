package pl.tokstudiow.api.university;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class GradingScaleController {

    private final GradingScaleService gradingScaleService;

    public GradingScaleController(GradingScaleService gradingScaleService) {
        this.gradingScaleService = gradingScaleService;
    }

    @GetMapping("/api/grading-scales")
    public List<GradingScaleResponse> list() {
        return gradingScaleService.list();
    }
}