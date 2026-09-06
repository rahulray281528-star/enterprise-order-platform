package com.enterprise.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notifications_customer", columnList = "customer_id"),
        @Index(name = "idx_notifications_order", columnList = "order_id"),
        @Index(name = "idx_notifications_created", columnList = "created_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    public enum Channel {
        EMAIL,
        SMS,
        PUSH
    }

    public enum DeliveryStatus {
        PENDING,
        SENT,
        FAILED
    }

    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    @Builder.Default
    private String id = UUID.randomUUID().toString();

    /**
     * The originating Kafka event id. Unique, so a redelivered event cannot produce a
     * second copy of the same notification.
     */
    @Column(name = "event_id", unique = true, length = 36)
    private String eventId;

    @Column(name = "customer_id", nullable = false, length = 36)
    private String customerId;

    @Column(name = "order_id", length = 36)
    private String orderId;

    @Column(name = "event_type", nullable = false, length = 60)
    private String eventType;

    @Column(name = "subject", nullable = false, length = 200)
    private String subject;

    @Column(name = "body", nullable = false, length = 2000)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    @Builder.Default
    private Channel channel = Channel.EMAIL;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false, length = 20)
    @Builder.Default
    private DeliveryStatus deliveryStatus = DeliveryStatus.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
