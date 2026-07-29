package vn.techflow.manager.task.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.techflow.manager.task.entity.WorkflowRun;

@Repository
public interface WorkflowRunRepository extends JpaRepository<WorkflowRun, Long> {
}
