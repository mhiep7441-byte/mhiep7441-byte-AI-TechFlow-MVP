package vn.techflow.manager.task;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.techflow.manager.task.entity.GenerationJob;
import vn.techflow.manager.task.entity.WorkflowRun;
import vn.techflow.manager.task.repository.GenerationJobRepository;
import vn.techflow.manager.task.repository.WorkflowRunRepository;

import java.util.List;

@Service
public class JobService {
    private final GenerationJobRepository jobRepository;
    private final WorkflowRunRepository workflowRunRepository;

    public JobService(GenerationJobRepository jobRepository, WorkflowRunRepository workflowRunRepository) {
        this.jobRepository = jobRepository;
        this.workflowRunRepository = workflowRunRepository;
    }

    @Transactional
    public WorkflowRun createWorkflow(Long campaignId, Long episodeId) {
        WorkflowRun run = new WorkflowRun();
        run.setCampaignId(campaignId);
        run.setEpisodeId(episodeId);
        run.setStatus("CREATED");
        return workflowRunRepository.save(run);
    }

    @Transactional
    public GenerationJob enqueueJob(Long workflowRunId, Long episodeId, String jobType, String inputJson, Integer priority) {
        GenerationJob job = new GenerationJob();
        job.setWorkflowRunId(workflowRunId);
        job.setEpisodeId(episodeId);
        job.setJobType(jobType);
        job.setInputJson(inputJson);
        job.setPriority(priority != null ? priority : 0);
        job.setStatus("QUEUED");
        return jobRepository.save(job);
    }
}
