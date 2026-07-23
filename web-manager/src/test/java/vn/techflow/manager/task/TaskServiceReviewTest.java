package vn.techflow.manager.task;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskServiceReviewTest {
    @Mock TaskRepository repository;

    @Test
    void reviewMovesOnlyGeneratedDraftToDone() {
        WorkTask task = task(TaskStatus.DRAFT_REQUIRES_REVIEW);
        when(repository.findById(1L)).thenReturn(Optional.of(task));
        when(repository.save(task)).thenReturn(task);
        TaskService service = new TaskService(repository, ".", "python", "video_worker.py");

        WorkTask reviewed = service.review(1L);

        assertEquals(TaskStatus.DONE, reviewed.getStatus());
        verify(repository).save(task);
    }

    @Test
    void reviewRejectsTasksThatHaveNoGeneratedDraft() {
        WorkTask task = task(TaskStatus.TODO);
        when(repository.findById(1L)).thenReturn(Optional.of(task));
        TaskService service = new TaskService(repository, ".", "python", "video_worker.py");

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.review(1L));

        assertEquals(409, error.getStatusCode().value());
        verify(repository, never()).save(any());
    }

    @Test
    void genericUpdateCannotSkipReviewGate() {
        WorkTask task = task(TaskStatus.DRAFT_REQUIRES_REVIEW);
        TaskService service = new TaskService(repository, ".", "python", "video_worker.py");

        TaskRequest request = new TaskRequest("Demo", "", "topic", TaskStatus.DONE,
                Priority.MEDIUM, null, "studio", "host", "https://docs.example.test");
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.save(task, request));

        assertEquals(409, error.getStatusCode().value());
        verify(repository, never()).save(any());
    }

    private static WorkTask task(TaskStatus status) {
        WorkTask task = new WorkTask();
        task.setTitle("Demo");
        task.setStatus(status);
        return task;
    }
}
