package vn.techflow.manager.youtube;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface YouTubeAccountRepository extends JpaRepository<YouTubeAccount, Long> {
    Optional<YouTubeAccount> findByOwnerId(Long ownerId);
    void deleteByOwnerId(Long ownerId);
}
