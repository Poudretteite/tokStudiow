package pl.tokstudiow.api.auth;

import org.jspecify.annotations.Nullable;

public record TokenResponse(String accessToken, @Nullable String refreshToken, String tokenType, long expiresIn) {

    TokenResponse withoutRefreshToken() {
        return new TokenResponse(accessToken, null, tokenType, expiresIn);
    }
}