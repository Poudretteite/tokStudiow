package pl.tokstudiow.api.dev;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import pl.tokstudiow.api.user.Role;
import pl.tokstudiow.api.user.User;
import pl.tokstudiow.api.user.UserRepository;

@Component
@Profile("dev")
public class DevAdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevAdminInitializer.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;

    public DevAdminInitializer(UserRepository users,
                               PasswordEncoder passwordEncoder,
                               @Value("${DEV_ADMIN_EMAIL}") String email,
                               @Value("${DEV_ADMIN_PASSWORD}") String password) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.existsByEmailIgnoreCase(email)) {
            return;
        }
        users.save(new User("Admin", "Dev", email, passwordEncoder.encode(password), Role.ADMIN));
        log.info("Created dev admin account: {}", email);
    }
}