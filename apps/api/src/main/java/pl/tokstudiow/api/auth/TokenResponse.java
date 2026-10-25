package pl.tokstudiow.api.auth;

public record TokenResponse(String accessToken, String refreshToken, String tokenType, long expiresIn) {
}