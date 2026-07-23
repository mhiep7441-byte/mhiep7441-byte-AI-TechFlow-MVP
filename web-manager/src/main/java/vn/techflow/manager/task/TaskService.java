package vn.techflow.manager.task;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
public class TaskService {
    private final TaskRepository repository;
    private final Path projectDirectory;
    private final String pythonCommand;
    private final String workerScript;

    public TaskService(
            TaskRepository repository,
            @Value("${techflow.project-dir:..}") String projectDirectory,
            @Value("${techflow.python-command:python}") String pythonCommand,
            @Value("${techflow.worker-script:video_worker.py}") String workerScript) {
        this.repository = repository;
        this.projectDirectory = Path.of(projectDirectory).toAbsolutePath().normalize();
        this.pythonCommand = pythonCommand;
        this.workerScript = workerScript;
    }

    public List<WorkTask> all() {
        return repository.findAllByOrderByUpdatedAtDesc();
    }

    public WorkTask get(Long id) {
        return repository.findById(id).orElseThrow(() -> new TaskNotFoundException(id));
    }

    public WorkTask save(WorkTask task, TaskRequest request) {
        TaskStatus requestedStatus = request.status();
        if (requestedStatus == null) {
            requestedStatus = task.getId() == null ? TaskStatus.TODO : task.getStatus();
        }
        // A generated draft may only be completed through the explicit review endpoint.
        // This prevents a raw PUT or a background process from skipping human review.
        if (task.getStatus() == TaskStatus.DRAFT_REQUIRES_REVIEW
                && requestedStatus != TaskStatus.DRAFT_REQUIRES_REVIEW) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Video chỉ được chuyển khỏi bản nháp bằng thao tác duyệt riêng");
        }
        if (requestedStatus == TaskStatus.DONE && task.getStatus() != TaskStatus.DONE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Hãy dùng thao tác duyệt bản nháp sau khi đã xem video");
        }
        task.setTitle(request.title().trim());
        task.setDescription(request.description() == null ? "" : request.description().trim());
        task.setTopic(request.topic() == null ? "" : request.topic().trim());
        task.setStatus(requestedStatus);
        task.setPriority(request.priority() == null ? Priority.MEDIUM : request.priority());
        task.setDueDate(request.dueDate());
        task.setVisualStyle(request.visualStyle() == null ? "" : request.visualStyle().trim());
        task.setCharacterDescription(request.characterDescription() == null ? "" : request.characterDescription().trim());
        task.setResearchSources(request.researchSources() == null ? "" : request.researchSources().trim());
        return repository.save(task);
    }

    public void delete(Long id) {
        repository.delete(get(id));
    }

    /**
     * Explicit human review gate for generated videos.
     *
     * A worker can only produce DRAFT_REQUIRES_REVIEW.  Marking a task as DONE
     * is deliberately a separate operation so a background worker (or an
     * accidental publication request) cannot skip the review step.
     */
    @Transactional
    public WorkTask review(Long id) {
        WorkTask task = get(id);
        if (task.getStatus() != TaskStatus.DRAFT_REQUIRES_REVIEW) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Video chỉ được duyệt khi đang ở trạng thái DRAFT_REQUIRES_REVIEW");
        }
        task.setStatus(TaskStatus.DONE);
        task.setErrorMessage(null);
        return repository.save(task);
    }

    @Async
    public CompletableFuture<Void> generate(Long id) {
        WorkTask task = get(id);
        if (task.getTopic().isBlank()) {
            throw new IllegalArgumentException("Công việc cần có chủ đề video");
        }
        task.setStatus(TaskStatus.GENERATING);
        task.setErrorMessage(null);
        task.setOutputPath(null);
        task.setQualityScore(null);
        task.setQualityStatus("NEEDS_REVIEW");
        task.setQualityReport("");
        repository.save(task);
        try {
            List<String> command = new ArrayList<>(List.of(pythonCommand, workerScript, "--topic", task.getTopic()));
            if (task.getResearchSources() != null && !task.getResearchSources().isBlank()) {
                command.addAll(List.of("--sources", task.getResearchSources().replaceAll("\\s*\\R\\s*", ",")));
            }
            if (task.getVisualStyle() != null && !task.getVisualStyle().isBlank()) {
                command.addAll(List.of("--visual-style", task.getVisualStyle()));
            }
            if (task.getCharacterDescription() != null && !task.getCharacterDescription().isBlank()) {
                command.addAll(List.of("--character", task.getCharacterDescription()));
            }
            Process process = new ProcessBuilder(command)
                    .directory(projectDirectory.toFile())
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("Pipeline lỗi " + exitCode + ": " + tail(output, 2500));
            }
            int marker = output.lastIndexOf("VIDEO_READY=");
            if (marker < 0) {
                throw new IOException("Worker không trả về URL video.");
            }
            task.setOutputPath(output.substring(marker + 12).trim());
            task.setQualityScore(parseQualityScore(marker(output, "QUALITY_SCORE=")));
            task.setQualityStatus(defaultValue(marker(output, "QUALITY_STATUS="), "NEEDS_REVIEW"));
            task.setQualityReport(defaultValue(marker(output, "QUALITY_REPORT="), "{}"));
            task.setStatus(TaskStatus.DRAFT_REQUIRES_REVIEW);
        } catch (Exception exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            task.setStatus(TaskStatus.FAILED);
            task.setErrorMessage(tail(exception.getMessage(), 3500));
        }
        repository.save(task);
        return CompletableFuture.completedFuture(null);
    }

    private static String marker(String output, String name) {
        int start = output.lastIndexOf(name);
        if (start < 0) return "";
        start += name.length();
        int end = output.indexOf('\n', start);
        return (end < 0 ? output.substring(start) : output.substring(start, end)).trim();
    }

    private static Integer parseQualityScore(String value) {
        try {
            if (value.isBlank()) return null;
            return Math.max(0, Math.min(100, Integer.parseInt(value)));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.substring(0, Math.min(value.length(), 8000));
    }

    private static String tail(String value, int limit) {
        if (value == null) return "Lỗi không xác định";
        return value.length() <= limit ? value : value.substring(value.length() - limit);
    }
}
