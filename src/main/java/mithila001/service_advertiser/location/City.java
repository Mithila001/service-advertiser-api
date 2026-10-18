package mithila001.service_advertiser.location;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "cities")
public class City {
    @Id private Integer id;
    @Column(nullable = false, length = 100) private String name;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "district_id") private District district;
    protected City() {}
    public Integer getId() { return id; }
    public String getName() { return name; }
    public District getDistrict() { return district; }
}
