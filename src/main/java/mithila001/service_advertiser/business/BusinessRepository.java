package mithila001.service_advertiser.business;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessRepository extends JpaRepository<Business, Integer> {
    Optional<Business> findByUniqueName(String uniqueName);
    boolean existsByUniqueName(String uniqueName);
}
