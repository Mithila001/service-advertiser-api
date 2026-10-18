package mithila001.service_advertiser.location;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "provinces")
public class Province {
    @Id private Integer id;
    @Column(nullable = false, unique = true, length = 100) private String name;
    protected Province() {}
    public Integer getId() { return id; }
    public String getName() { return name; }
}
