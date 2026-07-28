package vn.techflow.manager.research;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.techflow.manager.auth.AppUser;
import vn.techflow.manager.auth.AuthService;
import vn.techflow.manager.auth.UserRole;
import vn.techflow.manager.task.TaskRepository;
import vn.techflow.manager.task.WorkTask;

import java.util.List;

@Service
public class ResearchNotebookService {
    private final TaskRepository tasks;
    private final AuthService authService;
    private final ObjectMapper json;

    public ResearchNotebookService(TaskRepository tasks, AuthService authService, ObjectMapper json) {
        this.tasks = tasks;
        this.authService = authService;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public List<ResearchNotebookEntry> list(Authentication authentication, int limit) {
        AppUser owner = authService.current(authentication);
        Long ownerId = owner.getRole() == UserRole.ADMIN ? null : owner.getId();
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        return tasks.findResearchNotebook(ownerId, PageRequest.of(0, safeLimit)).stream()
                .map(this::entry)
                .toList();
    }

    private ResearchNotebookEntry entry(WorkTask task) {
        JsonNode research;
        try {
            research = json.readTree(task.getResearchJson());
        } catch (Exception exception) {
            research = json.createObjectNode();
        }
        return new ResearchNotebookEntry(
                task.getId(), task.getTitle(), task.getTopic(),
                research.path("summary").asText(""),
                array(research, "claims"), array(research, "sources"), array(research, "caveats"),
                research.path("mode").asText("unknown"),
                task.getFactCheckStatus(), task.getQualityScore(), task.getUpdatedAt());
    }

    private JsonNode array(JsonNode root, String field) {
        return root.path(field).isArray() ? root.path(field) : json.createArrayNode();
    }
}
