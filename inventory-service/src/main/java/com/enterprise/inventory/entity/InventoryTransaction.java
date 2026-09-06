package com.enterprise.inventory.entity;

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

/** Append-only audit trail. Every movement of stock is recorded, never overwritten. */
@Entity
@Table(name = "inventory_transactions", indexes = {
        @Index(name = "idx_inv_tx_order", columnList = "order_id"),
        @Index(name = "idx_inv_tx_product", columnList = "product_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryTransaction {

    public enum TransactionType {
        RESERVE,
        RELEASE,
        CONFIRM,
        RESTOCK
    }

    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    @Builder.Default
    private String id = UUID.randomUUID().toString();

    @Column(name = "product_id", nullable = false, length = 36)
    private String productId;

    @Column(name = "order_id", length = 36)
    private String orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 20)
    private TransactionType transactionType;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
