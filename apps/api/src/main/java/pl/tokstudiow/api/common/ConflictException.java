package pl.tokstudiow.api.common;

public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}