package mithila001.service_advertiser.category;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessCategoryRepository extends JpaRepository<BusinessCategory, Integer> {
    List<BusinessCategory> findAllByOrderByNameAsc();
}
