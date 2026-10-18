package mithila001.service_advertiser.business;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BusinessModerationRepository extends JpaRepository<BusinessModerationEvent, Long> {
    @Query("select e from BusinessModerationEvent e where e.business.id = :businessId "
            + "order by e.createdAt desc, e.id desc")
    List<BusinessModerationEvent> findHistory(Integer businessId);
}
