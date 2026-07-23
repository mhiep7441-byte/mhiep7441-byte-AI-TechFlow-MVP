package vn.techflow.manager.campaign;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:campaign-test")
@AutoConfigureMockMvc
class CampaignControllerTest {
    @Autowired
    MockMvc mvc;

    @Test
    void createsCampaignThenGeneratesEpisodes() throws Exception {
        String response = mvc.perform(post("/api/campaigns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "AI Agent từ cơ bản",
                                  "theme": "Xây AI Agent có kiểm chứng",
                                  "episodeCount": 3,
                                  "targetDurationSeconds": 180,
                                  "visualStyle": "Editorial motion"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PLANNING"))
                .andExpect(jsonPath("$.targetDurationSeconds").value(180))
                .andReturn().getResponse().getContentAsString();

        long id = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(response).get("id").asLong();

        mvc.perform(post("/api/campaigns/{id}/episodes", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].campaignId").value(id))
                .andExpect(jsonPath("$[0].episodeNumber").value(1))
                .andExpect(jsonPath("$[0].targetDurationSeconds").value(180));
    }

    @Test
    void rejectsCampaignThatIsTooLong() throws Exception {
        mvc.perform(post("/api/campaigns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Sai",
                                  "theme": "Sai",
                                  "episodeCount": 2,
                                  "targetDurationSeconds": 601
                                }
                                """))
                .andExpect(status().isBadRequest());
    }
}
