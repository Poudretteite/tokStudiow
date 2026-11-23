package pl.tokstudiow.api.studies;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Import wielu nowych studentów jednego kierunku, rocznika i semestru – wszystko albo nic.
 * Limit 300 osób na raz: każde hasło startowe jest haszowane BCryptem, co celowo trwa.
 */
public record StudentImportRequest(
        @NotNull UUID fieldOfStudyId,
        @NotNull @Min(2000) @Max(2100) Short startYear,
        @NotNull @Min(1) @Max(12) Short currentSemester,
        @NotEmpty @Size(max = 300) List<@Valid @NotNull Row> students
) {

    /** E-mail nie jest podawany: system tworzy go sam z inicjałów i numeru albumu. */
    public record Row(
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @NotBlank @Pattern(regexp = "^[0-9A-Za-z/-]{1,20}$") String albumNumber
    ) {
    }
}
