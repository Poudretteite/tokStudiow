package pl.tokstudiow.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Jeden zegar dla całej aplikacji. „Dzisiaj” liczy się w strefie uczelni,
 * a nie serwera – termin korekt nie przesuwa się, gdy serwer stoi w UTC.
 */
@Configuration
public class TimeConfig {

    @Bean
    Clock clock(@Value("${app.time-zone:Europe/Warsaw}") ZoneId zone) {
        return Clock.system(zone);
    }
}
