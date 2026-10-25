package pl.tokstudiow.api.auth;

public record LoginResponse(String accessToken, String tokenType, long expiresIn) {
}