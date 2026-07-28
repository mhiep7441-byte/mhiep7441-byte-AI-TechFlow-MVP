package vn.techflow.manager.campaign;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.time.LocalDateTime;
import java.util.List;

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

    long countByStatus(CampaignStatus status);
    long countByProductionEnabledTrue();

    @Query("""
            select c from Campaign c left join fetch c.owner
            where c.productionEnabled = true
              and c.status = vn.techflow.manager.campaign.CampaignStatus.ACTIVE
              and c.cadence <> vn.techflow.manager.campaign.CampaignCadence.MANUAL
              and c.nextRunAt is not null
              and c.nextRunAt <= :now
            order by c.nextRunAt asc
            """)
    List<Campaign> findDue(LocalDateTime now, Pageable pageable);
}
