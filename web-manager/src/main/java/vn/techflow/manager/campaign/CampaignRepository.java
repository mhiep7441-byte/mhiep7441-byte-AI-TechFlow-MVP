package vn.techflow.manager.campaign;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface CampaignRepository extends JpaRepository<Campaign, Long> {
    @Query("""
            select c from Campaign c left join fetch c.owner
            where (:ownerId is null or c.owner.id = :ownerId)
              and (:query = '' or lower(c.name) like lower(concat('%', :query, '%'))
                   or lower(c.theme) like lower(concat('%', :query, '%')))
            """)
    Page<Campaign> search(Long ownerId, String query, Pageable pageable);

    @Query("select c from Campaign c left join fetch c.owner where c.id = :id")
    Optional<Campaign> findWithOwnerById(Long id);
}
