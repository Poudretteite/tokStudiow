package pl.tokstudiow.api.common;

import java.util.List;

/** Operacja na wielu rekordach odrzucona w całości; lista mówi, które wiersze trzeba poprawić. */
public class RowValidationException extends RuntimeException {

    private final List<RowError> rows;

    public RowValidationException(String message, List<RowError> rows) {
        super(message);
        this.rows = List.copyOf(rows);
    }

    public List<RowError> getRows() {
        return rows;
    }
}
