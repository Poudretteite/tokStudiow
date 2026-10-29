package pl.tokstudiow.api.audit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import pl.tokstudiow.api.IntegrationTest;
import pl.tokstudiow.api.user.Role;
import pl.tokstudiow.api.user.User;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuditLogTest extends IntegrationTest {

    private User admin;

    @BeforeEach
    void setUp() {
        admin = createUser("Ada", "Admin", "admin@test.pl", Role.ADMIN);
        createUser("Anna", "Nowak", "anna@test.pl", Role.STUDENT);
    }

    @Test
    void creatingUserIsAuditedWithActorAndWithoutPassword() throws Exception {
        String janId = idOf(createUserViaApi("jan@test.pl").andExpect(status().isCreated()));

        auditHistory("user", janId, tokenFor("admin@test.pl"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].action").value("CREATE"))
                .andExpect(jsonPath("$[0].actorId").value(admin.getId().toString()))
                .andExpect(jsonPath("$[0].oldValue").value(nullValue()))
                .andExpect(jsonPath("$[0].newValue.email").value("jan@test.pl"))
                .andExpect(jsonPath("$[0].newValue.role").value("LECTURER"));

        String rawEntry = jdbc.sql("SELECT CAST(new_value AS text) FROM audit_log WHERE entity_id = :id")
                .param("id", UUID.fromString(janId))
                .query(String.class)
                .single();
        assertThat(rawEntry).doesNotContain("password").doesNotContain("$2a$");
    }

    @Test
    void deactivationRecordsStateBeforeAndAfter() throws Exception {
        String annaId = users.findByEmailIgnoreCase("anna@test.pl").orElseThrow().getId().toString();
        String adminToken = tokenFor("admin@test.pl");

        mvc.perform(post("/api/admin/users/{id}/deactivate", annaId).header(AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isNoContent());

        auditHistory("user", annaId, adminToken)
                .andExpect(jsonPath("$[0].action").value("UPDATE"))
                .andExpect(jsonPath("$[0].oldValue.active").value(true))
                .andExpect(jsonPath("$[0].newValue.active").value(false));
    }

    @Test
    void failedOperationLeavesNoAuditEntry() throws Exception {
        createUserViaApi("anna@test.pl").andExpect(status().isConflict());

        Long entries = jdbc.sql("SELECT count(*) FROM audit_log").query(Long.class).single();
        assertThat(entries).isZero();
    }

    @Test
    void auditEntriesCannotBeModifiedOrDeleted() throws Exception {
        createUserViaApi("jan@test.pl").andExpect(status().isCreated());

        assertThatThrownBy(() -> jdbc.sql("UPDATE audit_log SET action = 'DELETE'").update())
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.sql("DELETE FROM audit_log").update())
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.sql("TRUNCATE audit_log").update())
                .hasMessageContaining("append-only");
    }

    @Test
    void studentCannotReadAuditLog() throws Exception {
        auditHistory("user", UUID.randomUUID().toString(), tokenFor("anna@test.pl"))
                .andExpect(status().isForbidden());
    }

    // pomocnicze

    private ResultActions createUserViaApi(String email) throws Exception {
        return mvc.perform(post("/api/admin/users")
                .header(AUTHORIZATION, bearer(tokenFor("admin@test.pl")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"firstName": "Jan", "lastName": "Kowalski", "email": "%s",
                         "role": "LECTURER", "password": "%s"}
                        """.formatted(email, PASSWORD)));
    }
}
