package vn.techflow.manager.campaign;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import vn.techflow.manager.auth.*;
import vn.techflow.manager.task.TaskRepository;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:campaign-test")
@AutoConfigureMockMvc
class CampaignControllerTest {
    private static final String EMAIL = "series@example.com";
    @Autowired MockMvc mvc;
    @Autowired CampaignRepository campaigns;
    @Autowired TaskRepository tasks;
    @Autowired UserRepository users;

    @BeforeEach
    void setUp() {
        tasks.deleteAll();
        campaigns.deleteAll();
        users.deleteAll();
        AppUser user = new AppUser();
        user.setEmail(EMAIL);
        user.setDisplayName("Series Creator");
        user.setPasswordHash("not-used");
        user.setAuthProvider(AuthProvider.LOCAL);
        users.save(user);
    }

    @Test
    @WithMockUser(username = EMAIL, roles = "USER")
    void createsCampaignAndGeneratesOwnedEpisodes() throws Exception {
        String response = mvc.perform(post("/api/campaigns").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"AI Agent từ cơ bản",
                                  "theme":"Xây AI Agent có kiểm chứng",
                                  "episodeCount":3,
                                  "targetDurationSeconds":180,
                                  "visualStyle":"Editorial motion",
                                  "characterDescription":"Host nữ kỹ sư AI",
                                  "audience":"Người mới học AI",
                                  "cadence":"DAILY",
                                  "productionEnabled":true
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ownerName").value("Series Creator"))
                .andExpect(jsonPath("$.targetDurationSeconds").value(180))
                .andExpect(jsonPath("$.cadence").value("DAILY"))
                .andExpect(jsonPath("$.productionEnabled").value(true))
                .andReturn().getResponse().getContentAsString();

        long id = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response).path("id").asLong();
        mvc.perform(post("/api/campaigns/{id}/episodes", id).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].campaignId").value(id))
                .andExpect(jsonPath("$[0].ownerName").value("Series Creator"))
                .andExpect(jsonPath("$[0].targetDurationSeconds").value(180))
                .andExpect(jsonPath("$[0].visualStyle").value("Editorial motion"));
    }

    @Test
    @WithMockUser(username = EMAIL, roles = "USER")
    void aiSeriesPlansAndCreatesEpisodesInOneStep() throws Exception {
        String response = mvc.perform(post("/api/campaigns").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"AI Agent nhanh",
                                  "theme":"Xay AI Agent co kiem chung",
                                  "episodeCount":2,
                                  "targetDurationSeconds":60,
                                  "audience":"Lap trinh vien"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        long id = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response).path("id").asLong();
        mvc.perform(post("/api/campaigns/{id}/ai-series", id).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].campaignId").value(id))
                .andExpect(jsonPath("$[0].status").value("TODO"))
                .andExpect(jsonPath("$[1].status").value("TODO"));
    }

    @Test
    @WithMockUser(username = EMAIL, roles = "USER")
    void rejectsDurationAboveTenMinutes() throws Exception {
        mvc.perform(post("/api/campaigns").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Sai","theme":"Sai","episodeCount":2,"targetDurationSeconds":601}
                                """))
                .andExpect(status().isBadRequest());
    }
}
