package mithila001.service_advertiser.location;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "districts")
public class District {
    @Id private Integer id;
    @Column(nullable = false, length = 100) private String name;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "province_id") private Province province;
    protected District() {}
    public Integer getId() { return id; }
    public String getName() { return name; }
    public Province getProvince() { return province; }
}
