package vn.techflow.manager.task;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<WorkTask, Long> {
    List<WorkTask> findAllByStatus(TaskStatus status);
    List<WorkTask> findByCampaignIdOrderByEpisodeNumberAsc(Long campaignId);

    @Query("select t from WorkTask t left join fetch t.owner where t.id = :id")
    Optional<WorkTask> findWithOwnerById(Long id);

    @Query(value = """
            select t from WorkTask t left join fetch t.owner
            where (:ownerId is null or t.owner.id = :ownerId)
              and (:status is null or t.status = :status)
              and (:query = '' or lower(t.title) like lower(concat('%', :query, '%'))
                   or lower(t.topic) like lower(concat('%', :query, '%'))
                   or lower(t.description) like lower(concat('%', :query, '%')))
            """, countQuery = """
            select count(t) from WorkTask t
            where (:ownerId is null or t.owner.id = :ownerId)
              and (:status is null or t.status = :status)
              and (:query = '' or lower(t.title) like lower(concat('%', :query, '%'))
                   or lower(t.topic) like lower(concat('%', :query, '%'))
                   or lower(t.description) like lower(concat('%', :query, '%')))
            """)
    Page<WorkTask> search(Long ownerId, String query, TaskStatus status, Pageable pageable);

    @Query("select count(t) from WorkTask t where (:ownerId is null or t.owner.id = :ownerId)")
    long countVisible(Long ownerId);

    @Query("select count(t) from WorkTask t where (:ownerId is null or t.owner.id = :ownerId) and t.status = :status")
    long countVisibleByStatus(Long ownerId, TaskStatus status);

    @Query("select count(t) from WorkTask t where (:ownerId is null or t.owner.id = :ownerId) and t.status in :statuses")
    long countVisibleByStatusIn(Long ownerId, List<TaskStatus> statuses);
}
