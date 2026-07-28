package vn.techflow.manager.feedback;

import org.junit.jupiter.api.Test;
import vn.techflow.manager.auth.AuthService;
import vn.techflow.manager.task.TaskService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VideoFeedbackServiceTest {
    @Test
    void summaryReturnsRatingDistributionAndRoundedAverage() {
        VideoFeedbackRepository repository = mock(VideoFeedbackRepository.class);
        when(repository.count()).thenReturn(4L);
        when(repository.averageRating()).thenReturn(4.255);
        when(repository.countByRating(5)).thenReturn(2L);
        when(repository.countByRating(4)).thenReturn(1L);
        when(repository.countByRating(3)).thenReturn(1L);
        VideoFeedbackService service = new VideoFeedbackService(
                repository, mock(TaskService.class), mock(AuthService.class));

        FeedbackSummary summary = service.summary();

        assertThat(summary.total()).isEqualTo(4);
        assertThat(summary.average()).isEqualTo(4.26);
        assertThat(summary.distribution()).containsEntry(5, 2L).containsEntry(3, 1L);
    }
}
