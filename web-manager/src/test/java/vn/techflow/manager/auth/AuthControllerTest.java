package vn.techflow.manager.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import vn.techflow.manager.task.TaskRepository;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired TaskRepository tasks;

    @BeforeEach
    void setUp() {
        tasks.deleteAll();
        users.deleteAll();
    }

    @Test
    void registersAndPersistsAuthenticatedSession() throws Exception {
        MvcResult registration = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName":"Nguyễn Minh",
                                  "email":"minh@example.com",
                                  "password":"strong-pass-2026"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.csrfToken").isNotEmpty())
                .andReturn();

        MockHttpSession session = (MockHttpSession) registration.getRequest().getSession(false);
        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("minh@example.com"));
    }

    @Test
    void rejectsInvalidPassword() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"missing@example.com\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Email hoặc mật khẩu không đúng"));
    }
}
