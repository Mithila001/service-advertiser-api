package mithila001.service_advertiser.business;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface BusinessRepository extends JpaRepository<Business, Integer>, JpaSpecificationExecutor<Business> {
    Optional<Business> findByUniqueName(String uniqueName);
    boolean existsByUniqueName(String uniqueName);
}
