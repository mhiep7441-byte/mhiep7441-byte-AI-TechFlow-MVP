package vn.techflow.manager.tiktok;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import vn.techflow.manager.auth.AppUser;
import vn.techflow.manager.auth.AuthProvider;
import vn.techflow.manager.auth.UserRepository;
import vn.techflow.manager.task.TaskRepository;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TikTokControllerTest {
    private static final String EMAIL = "tiktok-creator@example.com";

    @Autowired MockMvc mvc;
    @Autowired TikTokAccountRepository accounts;
    @Autowired TaskRepository tasks;
    @Autowired UserRepository users;

    @BeforeEach
    void setUp() {
        accounts.deleteAll();
        tasks.deleteAll();
        users.deleteAll();
        AppUser user = new AppUser();
        user.setEmail(EMAIL);
        user.setDisplayName("TikTok Creator");
        user.setPasswordHash("not-used");
        user.setAuthProvider(AuthProvider.LOCAL);
        users.save(user);
    }

    @Test
    @WithMockUser(username = EMAIL, roles = "USER")
    void reportsUnconfiguredIntegrationWithoutLeakingCredentials() throws Exception {
        mvc.perform(get("/api/tiktok/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(false))
                .andExpect(jsonPath("$.connected").value(false))
                .andExpect(jsonPath("$.clientSecret").doesNotExist());
    }

    @Test
    @WithMockUser(username = EMAIL, roles = "USER")
    void requiresExplicitConsentBeforeDirectPost() throws Exception {
        mvc.perform(post("/api/tasks/999/publish/tiktok")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "consent": false,
                                  "privacyLevel": "SELF_ONLY",
                                  "title": "Video test"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validation.consent").exists());
    }
}
