package vn.techflow.manager.task;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
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

    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterruptedGenerations() {
        List<WorkTask> interruptedTasks = repository.findAllByStatus(TaskStatus.GENERATING);
        for (WorkTask task : interruptedTasks) {
            task.setStatus(TaskStatus.FAILED);
            task.setErrorMessage("Quá trình tạo video bị gián đoạn vì server khởi động lại. Hãy bấm Tạo video nháp để chạy lại.");
        }
        if (!interruptedTasks.isEmpty()) {
            repository.saveAll(interruptedTasks);
        }
    }

    public WorkTask get(Long id) {
        return repository.findById(id).orElseThrow(() -> new TaskNotFoundException(id));
    }

    public WorkTask save(WorkTask task, TaskRequest request) {
        task.setTitle(request.title().trim());
        task.setDescription(request.description() == null ? "" : request.description().trim());
        task.setTopic(request.topic() == null ? "" : request.topic().trim());
        task.setStatus(request.status() == null ? TaskStatus.TODO : request.status());
        task.setPriority(request.priority() == null ? Priority.MEDIUM : request.priority());
        task.setDueDate(request.dueDate());
        return repository.save(task);
    }

    public void delete(Long id) {
        repository.delete(get(id));
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
        repository.save(task);
        try {
            Process process = new ProcessBuilder(pythonCommand, workerScript, "--topic", task.getTopic())
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

    private static String tail(String value, int limit) {
        if (value == null) return "Lỗi không xác định";
        return value.length() <= limit ? value : value.substring(value.length() - limit);
    }
}
