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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
public class TaskService {
    private final TaskRepository repository;
    private final AuthService authService;
    private final Path projectDirectory;
    private final String pythonCommand;
    private final String workerScript;
    private final JobService jobService;

    public TaskService(
            TaskRepository repository,
            AuthService authService,
            @Value("${techflow.project-dir:..}") String projectDirectory,
            @Value("${techflow.python-command:python}") String pythonCommand,
            @Value("${techflow.worker-script:video_worker.py}") String workerScript,
            JobService jobService) {
        this.repository = repository;
        this.authService = authService;
        this.projectDirectory = Path.of(projectDirectory).toAbsolutePath().normalize();
        this.pythonCommand = pythonCommand;
        this.workerScript = workerScript;
        this.jobService = jobService;
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
            String inputJson = String.format("{\"episode_id\": %d, \"topic\": \"%s\", \"duration\": %d, \"visual_style\": \"%s\", \"character\": \"%s\", \"character_image\": \"%s\", \"audio_mode\": \"%s\", \"video_provider\": \"%s\", \"aspect_ratio\": \"%s\", \"render_quality\": \"%s\"}", 
                    task.getId(), 
                    task.getTopic().replace("\"", "\\\""), 
                    task.getTargetDurationSeconds(), 
                    task.getVisualStyle().replace("\"", "\\\""), 
                    task.getCharacterDescription().replace("\"", "\\\""), 
                    task.getCharacterImageUrl() == null ? "" : task.getCharacterImageUrl().replace("\"", "\\\""), 
                    task.getAudioMode(), 
                    task.getVideoProvider(), 
                    task.getAspectRatio(), 
                    task.getRenderQuality());
            
            vn.techflow.manager.task.entity.WorkflowRun run = jobService.createWorkflow(task.getCampaignId(), task.getId());
            jobService.enqueueJob(run.getId(), task.getId(), "GENERATE_SCRIPT", inputJson, 5);
            
            task.setWorkflowState("QUEUED_FOR_SCRIPT");
            repository.save(task);
        } catch (Exception exception) {
            task.setWorkflowState("FAILED");
            task.setStatus(TaskStatus.ERROR);
            repository.save(task);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Lỗi khi đẩy job vào queue", exception);
        }
        return CompletableFuture.completedFuture(null);
    }
    
    @Async
    public CompletableFuture<Void> render(Long id) {
        WorkTask task = internalGet(id);
        try {
            String inputJson = String.format("{\"episode_id\": %d, \"topic\": \"%s\", \"duration\": %d, \"visual_style\": \"%s\", \"character\": \"%s\", \"character_image\": \"%s\", \"audio_mode\": \"%s\", \"video_provider\": \"%s\", \"aspect_ratio\": \"%s\", \"render_quality\": \"%s\"}", 
                    task.getId(), 
                    task.getTopic().replace("\"", "\\\""), 
                    task.getTargetDurationSeconds(), 
                    task.getVisualStyle().replace("\"", "\\\""), 
                    task.getCharacterDescription().replace("\"", "\\\""), 
                    task.getCharacterImageUrl() == null ? "" : task.getCharacterImageUrl().replace("\"", "\\\""), 
                    task.getAudioMode(), 
                    task.getVideoProvider(), 
                    task.getAspectRatio(), 
                    task.getRenderQuality());
            
            vn.techflow.manager.task.entity.WorkflowRun run = jobService.createWorkflow(task.getCampaignId(), task.getId());
            jobService.enqueueJob(run.getId(), task.getId(), "GENERATE_VIDEO", inputJson, 7);
            
            task.setWorkflowState("QUEUED_FOR_VIDEO");
            task.setStatus(TaskStatus.GENERATING);
            repository.save(task);
        } catch (Exception exception) {
            task.setWorkflowState("FAILED");
            task.setStatus(TaskStatus.ERROR);
            repository.save(task);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Lỗi khi đẩy job render vào queue", exception);
        }
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
        if (request.targetDurationSeconds() != null) {
            task.setTargetDurationSeconds(request.targetDurationSeconds());
        } else if (creating) {
            task.setTargetDurationSeconds(60);
        }
        task.setVisualStyle(clean(request.visualStyle()));
        task.setCharacterDescription(clean(request.characterDescription()));
        task.setAudioMode(option(request.audioMode(), "narrated"));
        task.setVideoProvider(option(request.videoProvider(), "kenburns"));
        task.setAspectRatio(option(request.aspectRatio(), "9:16"));
        task.setRenderQuality(option(request.renderQuality(), "draft"));
        if (request.status() != null) task.setStatus(request.status());
        else if (creating) task.setStatus(TaskStatus.TODO);
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
    private static String option(String value, String fallback) {
        String cleaned = clean(value);
        return cleaned.isBlank() ? fallback : cleaned;
    }
    private static String tail(String value, int limit) {
        if (value == null) return "Lỗi không xác định";
        return value.length() <= limit ? value : value.substring(value.length() - limit);
    }
}
