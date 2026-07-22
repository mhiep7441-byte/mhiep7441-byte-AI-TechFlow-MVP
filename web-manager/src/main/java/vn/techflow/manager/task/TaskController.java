package vn.techflow.manager.task;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "Tasks")
@RestController
@RequestMapping("/api/tasks")
public class TaskController {
    private final TaskService service;

    public TaskController(TaskService service) { this.service = service; }

    @Operation(summary = "Danh sách công việc có phân trang")
    @GetMapping
    public Page<WorkTask> search(
            @RequestParam(defaultValue = "") String query,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            Authentication authentication) {
        return service.search(query, status, page, size, authentication);
    }

    @GetMapping("/{id}")
    public WorkTask one(@PathVariable Long id, Authentication authentication) {
        return service.getAccessible(id, authentication);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkTask create(@Valid @RequestBody TaskRequest request, Authentication authentication) {
        return service.create(request, authentication);
    }

    @PutMapping("/{id}")
    public WorkTask update(@PathVariable Long id, @Valid @RequestBody TaskRequest request,
                           Authentication authentication) {
        return service.update(id, request, authentication);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Authentication authentication) {
        service.delete(id, authentication);
    }

    @PostMapping("/{id}/generate")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> generate(@PathVariable Long id, Authentication authentication) {
        service.prepareGeneration(id, authentication);
        service.generate(id);
        return Map.of("message", "Đã đưa video vào pipeline");
    }
}
