package vn.techflow.manager.auth;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface UserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    long countByRoleAndEnabledTrue(UserRole role);

    @Query("""
            select u from AppUser u
            where (:query = '' or lower(u.email) like lower(concat('%', :query, '%'))
                   or lower(u.displayName) like lower(concat('%', :query, '%')))
              and (:role is null or u.role = :role)
            """)
    Page<AppUser> search(String query, UserRole role, Pageable pageable);
}
