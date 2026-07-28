package vn.techflow.manager.dashboard;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AdminDashboardControllerTest {
    @Autowired MockMvc mvc;

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanReadGlobalOperationsMetrics() throws Exception {
        mvc.perform(get("/api/admin/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users").isNumber())
                .andExpect(jsonPath("$.campaigns").isNumber())
                .andExpect(jsonPath("$.videos").isNumber())
                .andExpect(jsonPath("$.awaitingReview").isNumber());
    }

    @Test
    @WithMockUser(roles = "USER")
    void regularUserCannotReadAdminDashboard() throws Exception {
        mvc.perform(get("/api/admin/dashboard"))
                .andExpect(status().isForbidden());
    }
}
