package vn.techflow.manager.tiktok;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface TikTokAccountRepository extends JpaRepository<TikTokAccount, Long> {
    @Query("select a from TikTokAccount a join fetch a.owner where a.owner.id = :ownerId")
    Optional<TikTokAccount> findByOwnerId(Long ownerId);
}
