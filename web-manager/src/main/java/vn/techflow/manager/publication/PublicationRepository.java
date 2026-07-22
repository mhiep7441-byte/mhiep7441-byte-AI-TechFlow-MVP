package vn.techflow.manager.publication;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PublicationRepository extends JpaRepository<Publication, Long> {
    List<Publication> findAllByOrderByScheduledAtAscCreatedAtDesc();
}
