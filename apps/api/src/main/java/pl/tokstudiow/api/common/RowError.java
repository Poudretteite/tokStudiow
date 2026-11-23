package pl.tokstudiow.api.common;

/**
 * Błąd jednego wiersza w operacji na wielu rekordach (np. imporcie studentów).
 *
 * @param row   numer wiersza w żądaniu, od 1
 * @param field pole, którego dotyczy błąd
 */
public record RowError(int row, String field, String message) {
}
