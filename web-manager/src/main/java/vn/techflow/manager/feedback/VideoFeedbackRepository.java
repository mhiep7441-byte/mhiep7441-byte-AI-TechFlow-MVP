package vn.techflow.manager.feedback;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface VideoFeedbackRepository extends JpaRepository<VideoFeedback, Long> {
    Optional<VideoFeedback> findByOwnerIdAndTaskId(Long ownerId, Long taskId);

    @Query(value = """
            select f from VideoFeedback f join fetch f.owner join fetch f.task
            where (:rating is null or f.rating = :rating)
            order by f.updatedAt desc
            """,
            countQuery = "select count(f) from VideoFeedback f where (:rating is null or f.rating = :rating)")
    Page<VideoFeedback> search(Integer rating, Pageable pageable);

    @Query("select coalesce(avg(f.rating), 0) from VideoFeedback f")
    double averageRating();

    long countByRating(Integer rating);
}
