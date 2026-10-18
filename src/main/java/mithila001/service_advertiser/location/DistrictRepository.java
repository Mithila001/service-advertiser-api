package mithila001.service_advertiser.location;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DistrictRepository extends JpaRepository<District, Integer> {
    List<District> findAllByOrderByNameAsc();
    List<District> findAllByProvinceIdOrderByNameAsc(Integer provinceId);
}
