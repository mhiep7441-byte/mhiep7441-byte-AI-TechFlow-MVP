package vn.techflow.manager.campaign;

import org.junit.jupiter.api.Test;
import vn.techflow.manager.task.TaskService;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CampaignProductionSchedulerTest {
    @Test
    void generatesOnlyTasksClaimedByDueCampaignBatch() {
        CampaignService campaigns = mock(CampaignService.class);
        TaskService tasks = mock(TaskService.class);
        when(campaigns.claimDueBatch(any(LocalDateTime.class), eq(3))).thenReturn(List.of(11L, 12L));

        new CampaignProductionScheduler(campaigns, tasks).produceDueDrafts();

        verify(tasks).generate(11L);
        verify(tasks).generate(12L);
        verifyNoMoreInteractions(tasks);
    }
}
