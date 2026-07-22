package vn.techflow.manager.task;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.techflow.manager.auth.AppUser;
import vn.techflow.manager.auth.AuthService;
import vn.techflow.manager.auth.UserRole;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
public class TaskService {
    private final TaskRepository repository;
    private final AuthService authService;
    private final Path projectDirectory;
    private final String pythonCommand;
    private final String workerScript;

    public TaskService(
            TaskRepository repository,
            AuthService authService,
            @Value("${techflow.project-dir:..}") String projectDirectory,
            @Value("${techflow.python-command:python}") String pythonCommand,
            @Value("${techflow.worker-script:video_worker.py}") String workerScript) {
        this.repository = repository;
        this.authService = authService;
        this.projectDirectory = Path.of(projectDirectory).toAbsolutePath().normalize();
        this.pythonCommand = pythonCommand;
        this.workerScript = workerScript;
    }

    @Transactional(readOnly = true)
    public Page<WorkTask> search(String query, TaskStatus status, int page, int size, Authentication authentication) {
        AppUser viewer = authService.current(authentication);
        Long ownerId = viewer.getRole() == UserRole.ADMIN ? null : viewer.getId();
        int safeSize = Math.min(Math.max(size, 1), 50);
        return repository.search(ownerId, query == null ? "" : query.trim(), status,
                PageRequest.of(Math.max(page, 0), safeSize, Sort.by(Sort.Direction.DESC, "updatedAt")));
    }

    @Transactional(readOnly = true)
    public WorkTask getAccessible(Long id, Authentication authentication) {
        WorkTask task = internalGet(id);
        AppUser viewer = authService.current(authentication);
        if (viewer.getRole() != UserRole.ADMIN && (task.getOwner() == null || !viewer.getId().equals(task.getOwner().getId()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền truy cập video này");
        }
        return task;
    }

    @Transactional
    public WorkTask create(TaskRequest request, Authentication authentication) {
        WorkTask task = new WorkTask();
        task.setOwner(authService.current(authentication));
        apply(task, request, true);
        return repository.save(task);
    }

    @Transactional
    public WorkTask update(Long id, TaskRequest request, Authentication authentication) {
        WorkTask task = getAccessible(id, authentication);
        apply(task, request, false);
        return repository.save(task);
    }

    @Transactional
    public void delete(Long id, Authentication authentication) {
        repository.delete(getAccessible(id, authentication));
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void recoverInterruptedGenerations() {
        List<WorkTask> interruptedTasks = repository.findAllByStatus(TaskStatus.GENERATING);
        for (WorkTask task : interruptedTasks) {
            task.setStatus(TaskStatus.FAILED);
            task.setErrorMessage("Quá trình tạo video bị gián đoạn vì server khởi động lại. Hãy chạy lại video.");
        }
        if (!interruptedTasks.isEmpty()) repository.saveAll(interruptedTasks);
    }

    @Transactional
    public void prepareGeneration(Long id, Authentication authentication) {
        WorkTask task = getAccessible(id, authentication);
        if (task.getStatus() == TaskStatus.GENERATING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Video đã có trong hàng đợi");
        }
        if (task.getTopic().isBlank()) throw new IllegalArgumentException("Video cần có chủ đề");
        task.setStatus(TaskStatus.GENERATING);
        task.setErrorMessage(null);
        task.setOutputPath(null);
        repository.save(task);
    }

    @Async
    public CompletableFuture<Void> generate(Long id) {
        WorkTask task = internalGet(id);
        try {
            Process process = new ProcessBuilder(pythonCommand, workerScript, "--topic", task.getTopic())
                    .directory(projectDirectory.toFile())
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.waitFor();
            if (exitCode != 0) throw new IOException("Pipeline lỗi " + exitCode + ": " + tail(output, 2500));
            int marker = output.lastIndexOf("VIDEO_READY=");
            if (marker < 0) throw new IOException("Worker không trả về URL video");
            String videoUrl = output.substring(marker + 12).lines().findFirst().orElse("").trim();
            if (videoUrl.isBlank()) throw new IOException("Worker trả về URL video trống");
            task.setOutputPath(videoUrl);
            if (task.getCaption().isBlank()) task.setCaption(task.getTitle());
            if (task.getHashtags().isBlank()) task.setHashtags("#AI #congnghe #laptrinh #TechFlowVN");
            task.setStatus(TaskStatus.DRAFT_REQUIRES_REVIEW);
        } catch (Exception exception) {
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            task.setStatus(TaskStatus.FAILED);
            task.setErrorMessage(tail(exception.getMessage(), 3500));
        }
        repository.save(task);
        return CompletableFuture.completedFuture(null);
    }

    private WorkTask internalGet(Long id) {
        return repository.findWithOwnerById(id).orElseThrow(() -> new TaskNotFoundException(id));
    }

    private void apply(WorkTask task, TaskRequest request, boolean creating) {
        if (request.status() == TaskStatus.GENERATING) {
            throw new IllegalArgumentException("Trạng thái đang dựng chỉ do pipeline quản lý");
        }
        task.setTitle(request.title().trim());
        task.setDescription(clean(request.description()));
        task.setTopic(clean(request.topic()));
        task.setCaption(clean(request.caption()));
        task.setHashtags(clean(request.hashtags()));
        task.setPriority(request.priority() == null ? Priority.MEDIUM : request.priority());
        task.setDueDate(request.dueDate());
        if (request.status() != null) task.setStatus(request.status());
        else if (creating) task.setStatus(TaskStatus.TODO);
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
    private static String tail(String value, int limit) {
        if (value == null) return "Lỗi không xác định";
        return value.length() <= limit ? value : value.substring(value.length() - limit);
    }
}
