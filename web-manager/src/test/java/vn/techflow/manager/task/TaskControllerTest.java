package vn.techflow.manager.task;

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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TaskControllerTest {
    private static final String EMAIL = "creator@example.com";

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired TaskRepository tasks;

    @BeforeEach
    void setUp() {
        tasks.deleteAll();
        users.deleteAll();
        AppUser user = new AppUser();
        user.setEmail(EMAIL);
        user.setDisplayName("Creator");
        user.setPasswordHash("not-used-in-this-test");
        user.setAuthProvider(AuthProvider.LOCAL);
        users.save(user);
    }

    @Test
    @WithMockUser(username = EMAIL, roles = "USER")
    void createsTaskForAuthenticatedOwner() throws Exception {
        mvc.perform(post("/api/tasks")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Video Docker","topic":"Docker là gì?"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("TODO"))
                .andExpect(jsonPath("$.ownerName").value("Creator"));
    }

    @Test
    @WithMockUser(username = EMAIL, roles = "USER")
    void validatesRequiredFields() throws Exception {
        mvc.perform(post("/api/tasks")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\",\"topic\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validation.title").exists())
                .andExpect(jsonPath("$.validation.topic").exists());
    }

    @Test
    void rejectsAnonymousApiRequests() throws Exception {
        mvc.perform(get("/api/tasks"))
                .andExpect(status().isUnauthorized());
    }
}
