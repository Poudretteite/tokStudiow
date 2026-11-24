package pl.tokstudiow.api.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import pl.tokstudiow.api.auth.JwtProperties;
import pl.tokstudiow.api.auth.TokenService;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.List;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    static final String PASSWORD_CHANGE_REQUIRED = "PASSWORD_CHANGE_REQUIRED";

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/refresh", "/api/auth/logout").permitAll()
                        .requestMatchers("/error").permitAll()
                        // konto z hasłem startowym może tylko zmienić hasło i odczytać, kim jest
                        .requestMatchers(HttpMethod.POST, "/api/auth/password").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/me", "/api/auth/me").authenticated()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/dean-office/**").hasRole("DEAN_OFFICE")
                        .requestMatchers("/api/lecturer/**").hasRole("LECTURER")
                        .anyRequest().access(passwordChanged()))
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(UserDetailsService userDetailsService,
                                                       PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    @Bean
    public JwtEncoder jwtEncoder(JwtProperties properties) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey(properties)));
    }

    @Bean
    public JwtDecoder jwtDecoder(JwtProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey(properties))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(TokenService.ISSUER));
        return decoder;
    }

    /**
     * Token konta z hasłem startowym nie niesie roli, tylko uprawnienie PASSWORD_CHANGE_REQUIRED.
     * Dzięki temu nie przejdzie ani reguł z adresów (hasRole), ani {@code @PreAuthorize} w kontrolerach.
     */
    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("role");
        roles.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt ->
                Boolean.TRUE.equals(jwt.getClaimAsBoolean(TokenService.PASSWORD_CHANGE_CLAIM))
                        ? List.<GrantedAuthority>of(new SimpleGrantedAuthority(PASSWORD_CHANGE_REQUIRED))
                        : roles.convert(jwt));
        return converter;
    }

    /** Pozostałe adresy: zalogowany i bez obowiązku zmiany hasła. */
    private static AuthorizationManager<RequestAuthorizationContext> passwordChanged() {
        return (authentication, context) -> {
            Authentication auth = authentication.get();
            boolean allowed = auth != null && auth.isAuthenticated()
                    && !(auth instanceof AnonymousAuthenticationToken)
                    && auth.getAuthorities().stream()
                    .noneMatch(a -> PASSWORD_CHANGE_REQUIRED.equals(a.getAuthority()));
            return new AuthorizationDecision(allowed);
        };
    }

    private static SecretKey secretKey(JwtProperties properties) {
        byte[] bytes = Base64.getDecoder().decode(properties.secret());
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 bytes (base64)");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }
}