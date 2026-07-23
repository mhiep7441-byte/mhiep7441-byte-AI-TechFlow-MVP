package vn.techflow.manager.publication;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import vn.techflow.manager.task.TaskService;
import vn.techflow.manager.task.TaskStatus;
import vn.techflow.manager.task.WorkTask;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PublicationServiceTest {
    @Mock PublicationRepository repository;
    @Mock TaskService taskService;

    @Test
    void refusesPublishedPublicationUntilTaskHasBeenReviewed() {
        WorkTask draft = task(TaskStatus.DRAFT_REQUIRES_REVIEW);
        when(taskService.get(7L)).thenReturn(draft);
        PublicationService service = new PublicationService(repository, taskService);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.create(new PublicationRequest(7L, Platform.TIKTOK,
                        PublicationStatus.PUBLISHED, LocalDateTime.now(), null, "")));

        assertEquals(409, error.getStatusCode().value());
        verify(repository, never()).save(any());
    }

    @Test
    void allowsPublishedPublicationAfterExplicitReview() {
        WorkTask reviewed = task(TaskStatus.DONE);
        reviewed.setOutputPath("https://res.cloudinary.com/example/video.mp4");
        reviewed.setOutputPath("https://res.cloudinary.com/demo/video/upload/techflow/demo.mp4");
        when(taskService.get(7L)).thenReturn(reviewed);
        when(repository.save(any(Publication.class))).thenAnswer(invocation -> invocation.getArgument(0));
        PublicationService service = new PublicationService(repository, taskService);

        PublicationResponse response = service.create(new PublicationRequest(7L, Platform.TIKTOK,
                PublicationStatus.PUBLISHED, null, "tiktok-post-1", "approved by editor"));

        assertEquals(PublicationStatus.PUBLISHED, response.status());
        verify(repository).save(any(Publication.class));
    }

    @Test
    void refusesPublicationWhenApprovedTaskHasNoVideoFile() {
        WorkTask reviewed = task(TaskStatus.DONE);
        when(taskService.get(7L)).thenReturn(reviewed);
        PublicationService service = new PublicationService(repository, taskService);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.create(new PublicationRequest(7L, Platform.TIKTOK,
                        PublicationStatus.READY, LocalDateTime.now(), null, "")));

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
