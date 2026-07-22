package vn.techflow.manager.task;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskServiceRecoveryTest {
    @Test
    void marksInterruptedGenerationsAsFailedAfterRestart() {
        TaskRepository repository = mock(TaskRepository.class);
        vn.techflow.manager.auth.AuthService authService = mock(vn.techflow.manager.auth.AuthService.class);
        WorkTask interrupted = new WorkTask();
        interrupted.setStatus(TaskStatus.GENERATING);
        when(repository.findAllByStatus(TaskStatus.GENERATING)).thenReturn(List.of(interrupted));
        TaskService service = new TaskService(repository, authService, ".", "python", "video_worker.py");

        service.recoverInterruptedGenerations();

        assertEquals(TaskStatus.FAILED, interrupted.getStatus());
        assertTrue(interrupted.getErrorMessage().contains("server khởi động lại"));
        verify(repository).saveAll(List.of(interrupted));
    }
}
