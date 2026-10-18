package mithila001.service_advertiser.business;

import java.time.LocalDateTime;
import jakarta.persistence.*;
import mithila001.service_advertiser.user.User;

@Entity
@Table(name = "business_moderation_history")
public class BusinessModerationEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_id", nullable = false)
    private Business business;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "admin_id", nullable = false)
    private User admin;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BusinessModerationAction action;
    @Column(length = 500)
    private String reason;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected BusinessModerationEvent() {}

    public BusinessModerationEvent(Business business, User admin, BusinessModerationAction action,
            String reason, LocalDateTime createdAt) {
        this.business = business;
        this.admin = admin;
        this.action = action;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Business getBusiness() { return business; }
    public User getAdmin() { return admin; }
    public BusinessModerationAction getAction() { return action; }
    public String getReason() { return reason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
