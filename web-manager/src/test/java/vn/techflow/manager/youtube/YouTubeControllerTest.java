package vn.techflow.manager.youtube;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import vn.techflow.manager.auth.AppUser;
import vn.techflow.manager.auth.AuthProvider;
import vn.techflow.manager.auth.UserRepository;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class YouTubeControllerTest {
    private static final String EMAIL = "youtube-status@example.com";
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;

    @BeforeEach
    void setUp() {
        if (users.findByEmailIgnoreCase(EMAIL).isEmpty()) {
            AppUser user = new AppUser();
            user.setEmail(EMAIL);
            user.setDisplayName("YouTube Creator");
            user.setPasswordHash("not-used");
            user.setAuthProvider(AuthProvider.LOCAL);
            users.save(user);
        }
    }

    @Test
    @WithMockUser(username = EMAIL, roles = "USER")
    void reportsSafeDisconnectedStateWhenServerCredentialsAreMissing() throws Exception {
        mvc.perform(get("/api/youtube/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(false))
                .andExpect(jsonPath("$.connected").value(false))
                .andExpect(jsonPath("$.canUpload").value(false));
    }
}
