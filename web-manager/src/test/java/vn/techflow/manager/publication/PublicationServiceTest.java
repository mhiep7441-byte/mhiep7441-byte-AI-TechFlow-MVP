package vn.techflow.manager.publication;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import vn.techflow.manager.auth.AuthService;
import vn.techflow.manager.task.TaskService;
import vn.techflow.manager.task.TaskStatus;
import vn.techflow.manager.task.WorkTask;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PublicationServiceTest {
    @Test
    void approvingReviewedDraftMakesPlanReady() {
        PublicationRepository repository = mock(PublicationRepository.class);
        TaskService tasks = mock(TaskService.class);
        PublicationService service = new PublicationService(repository, tasks, mock(AuthService.class));
        WorkTask task = new WorkTask();
        task.setTitle("Video AI");
        task.setStatus(TaskStatus.DRAFT_REQUIRES_REVIEW);
        task.setOutputPath("https://cdn.example/video.mp4");
        Publication publication = new Publication();
        publication.setTask(task);
        publication.setPlatform(Platform.TIKTOK);
        publication.setStatus(PublicationStatus.PENDING);
        when(repository.findWithTaskById(8L)).thenReturn(Optional.of(publication));
        when(tasks.getAccessible(isNull(), any())).thenReturn(task);
        when(repository.save(publication)).thenReturn(publication);

        var authentication = new TestingAuthenticationToken("user", "password");
        service.approve(8L, new PublicationApprovalRequest(true), authentication);

        assertThat(publication.getStatus()).isEqualTo(PublicationStatus.READY);
        assertThat(task.getStatus()).isEqualTo(TaskStatus.DONE);
        assertThat(publication.getNote()).contains("Video Studio");
    }

    @Test
    void clientCannotMarkPublicationReadyDirectly() {
        PublicationRepository repository = mock(PublicationRepository.class);
        TaskService tasks = mock(TaskService.class);
        PublicationService service = new PublicationService(repository, tasks, mock(AuthService.class));
        WorkTask task = new WorkTask();
        when(tasks.getAccessible(eq(3L), any())).thenReturn(task);
        var authentication = new TestingAuthenticationToken("user", "password");

        assertThatThrownBy(() -> service.create(
                new PublicationRequest(3L, Platform.TIKTOK, PublicationStatus.READY, null, "external", ""),
                authentication))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
    }
}
