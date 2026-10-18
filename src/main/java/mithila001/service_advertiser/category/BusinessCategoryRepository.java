package mithila001.service_advertiser.category;

import java.util.List;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessCategoryRepository extends JpaRepository<BusinessCategory, Integer> {
    List<BusinessCategory> findAllByOrderByNameAsc();
    long countByIdIn(Collection<Integer> ids);
}
