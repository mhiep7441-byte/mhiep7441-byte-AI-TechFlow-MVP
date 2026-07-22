package vn.techflow.manager.publication;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface PublicationRepository extends JpaRepository<Publication, Long> {
    @Query(value = """
            select p from Publication p join fetch p.task t left join fetch t.owner
            where (:ownerId is null or t.owner.id = :ownerId)
              and (:status is null or p.status = :status)
            """,
            countQuery = """
            select count(p) from Publication p join p.task t
            where (:ownerId is null or t.owner.id = :ownerId)
              and (:status is null or p.status = :status)
            """)
    Page<Publication> search(Long ownerId, PublicationStatus status, Pageable pageable);

    @Query("select p from Publication p join fetch p.task t left join fetch t.owner where p.id = :id")
    Optional<Publication> findWithTaskById(Long id);

    @Query("select p from Publication p join fetch p.task t left join fetch t.owner where p.externalId = :externalId")
    Optional<Publication> findWithTaskByExternalId(String externalId);

    @Query("select count(p) from Publication p join p.task t where (:ownerId is null or t.owner.id = :ownerId)")
    long countVisible(Long ownerId);
}
