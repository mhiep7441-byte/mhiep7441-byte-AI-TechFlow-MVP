package vn.techflow.manager.research;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.TestingAuthenticationToken;
import vn.techflow.manager.auth.AppUser;
import vn.techflow.manager.auth.AuthService;
import vn.techflow.manager.task.TaskRepository;
import vn.techflow.manager.task.WorkTask;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResearchNotebookServiceTest {
    @Test
    void mapsStoredResearchAndCapsPageSize() {
        TaskRepository tasks = mock(TaskRepository.class);
        AuthService auth = mock(AuthService.class);
        AppUser owner = new AppUser();
        WorkTask task = new WorkTask();
        task.setTitle("Gemini 3");
        task.setTopic("AI");
        task.setResearchJson("""
                {"summary":"Tóm tắt có nguồn","mode":"online",
                 "sources":[{"id":"S1","url":"https://example.com"}],
                 "claims":[{"text":"Claim"}]}
                """);
        var authentication = new TestingAuthenticationToken("user", "password");
        when(auth.current(authentication)).thenReturn(owner);
        when(tasks.findResearchNotebook(eq(null), any(Pageable.class))).thenReturn(List.of(task));

        var entries = new ResearchNotebookService(tasks, auth, new ObjectMapper())
                .list(authentication, 999);

        assertThat(entries).hasSize(1);
        assertThat(entries.getFirst().summary()).isEqualTo("Tóm tắt có nguồn");
        assertThat(entries.getFirst().sources()).hasSize(1);
    }
}
