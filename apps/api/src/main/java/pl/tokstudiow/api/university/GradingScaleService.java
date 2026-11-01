package pl.tokstudiow.api.university;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tokstudiow.api.common.NotFoundException;

import java.util.List;
import java.util.UUID;

@Service
public class GradingScaleService {

    private final GradingScaleRepository scales;

    public GradingScaleService(GradingScaleRepository scales) {
        this.scales = scales;
    }

    @Transactional(readOnly = true)
    public List<GradingScaleResponse> list() {
        return scales.findAllWithValues().stream().map(GradingScaleResponse::from).toList();
    }

    GradingScale find(UUID id) {
        return scales.findById(id).orElseThrow(() -> new NotFoundException("Grading scale not found"));
    }
}