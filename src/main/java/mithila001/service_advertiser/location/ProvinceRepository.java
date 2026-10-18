package mithila001.service_advertiser.location;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProvinceRepository extends JpaRepository<Province, Integer> {
    List<Province> findAllByOrderByNameAsc();
}
