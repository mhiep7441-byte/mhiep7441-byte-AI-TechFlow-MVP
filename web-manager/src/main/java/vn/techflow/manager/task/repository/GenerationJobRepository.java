package vn.techflow.manager.task.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import vn.techflow.manager.task.entity.GenerationJob;

import java.util.Optional;

@Repository
public interface GenerationJobRepository extends JpaRepository<GenerationJob, Long> {

    @Query(value = "SELECT * FROM generation_jobs WHERE status = 'QUEUED' " +
            "ORDER BY priority DESC, queued_at ASC LIMIT 1 FOR UPDATE SKIP LOCKED", nativeQuery = true)
    Optional<GenerationJob> claimNextJob();

    long countByStatus(String status);
}
