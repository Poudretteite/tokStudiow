package pl.tokstudiow.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Zadania okresowe, np. sprzątanie starych liczników nieudanych logowań. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
