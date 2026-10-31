package mithila001.service_advertiser.item;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.*;

public interface ItemRepository extends JpaRepository<Item, Long>, JpaSpecificationExecutor<Item> {
    @Override
    @EntityGraph(attributePaths = "business")
    Page<Item> findAll(org.springframework.data.jpa.domain.Specification<Item> spec, Pageable pageable);

    long countByBusinessId(Integer businessId);
    List<Item> findByBusinessIdOrderByIdAsc(Integer businessId);
    Optional<Item> findByIdAndBusinessId(Long id, Integer businessId);

    @EntityGraph(attributePaths = "business")
    @Query("select i from Item i, Business b where i.businessId = b.id and i.id = :id "
            + "and i.status = mithila001.service_advertiser.item.ItemStatus.ACTIVE "
            + "and b.status = mithila001.service_advertiser.business.BusinessStatus.ACTIVE")
    Optional<Item> findPublicById(Long id);

    @EntityGraph(attributePaths = "business")
    @Query("select i from Item i, Business b where i.businessId = b.id and b.uniqueName = :uniqueName "
            + "and i.status = mithila001.service_advertiser.item.ItemStatus.ACTIVE "
            + "and b.status = mithila001.service_advertiser.business.BusinessStatus.ACTIVE")
    Page<Item> findPublicByBusiness(String uniqueName, Pageable pageable);
}
