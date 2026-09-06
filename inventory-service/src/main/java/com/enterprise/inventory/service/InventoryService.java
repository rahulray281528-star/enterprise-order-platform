package com.enterprise.inventory.service;

import com.enterprise.common.exception.InsufficientInventoryException;
import com.enterprise.common.exception.ResourceNotFoundException;
import com.enterprise.inventory.dto.InventoryResponse;
import com.enterprise.inventory.dto.ReservationItem;
import com.enterprise.inventory.dto.ReservationRequest;
import com.enterprise.inventory.dto.ReservationResponse;
import com.enterprise.inventory.entity.Inventory;
import com.enterprise.inventory.entity.InventoryTransaction;
import com.enterprise.inventory.repository.InventoryRepository;
import com.enterprise.inventory.repository.InventoryTransactionRepository;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryTransactionRepository transactionRepository;

    public InventoryService(InventoryRepository inventoryRepository,
                            InventoryTransactionRepository transactionRepository) {
        this.inventoryRepository = inventoryRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional(readOnly = true)
    public InventoryResponse getByProductId(String productId) {
        return inventoryRepository.findByProductId(productId)
                .map(InventoryResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory for product", productId));
    }

    /**
     * Reserves stock for an order, all-or-nothing.
     *
     * <p>Runs in a single transaction and takes a pessimistic write lock on each product
     * row. If any line cannot be satisfied the exception rolls the whole transaction
     * back, so an order never ends up half-reserved.</p>
     *
     * <p>Idempotent: a repeated call for an order that already has a RESERVE transaction
     * returns the existing reservation instead of holding stock twice. This matters
     * because the caller retries on timeout.</p>
     */
    @Transactional
    public ReservationResponse reserve(ReservationRequest request) {
        String orderId = request.getOrderId();

        if (transactionRepository.existsByOrderIdAndTransactionType(
                orderId, InventoryTransaction.TransactionType.RESERVE)) {
            log.info("Reservation for order {} already exists - returning existing reservation", orderId);
            return ReservationResponse.builder()
                    .orderId(orderId)
                    .reservationId(orderId)
                    .reserved(true)
                    .message("Already reserved")
                    .build();
        }

        // Deterministic lock ordering. Two orders containing the same two products in
        // opposite order would otherwise be able to deadlock against each other.
        List<ReservationItem> items = request.getItems().stream()
                .sorted((a, b) -> a.getProductId().compareTo(b.getProductId()))
                .toList();

        for (ReservationItem item : items) {
            Inventory inventory = inventoryRepository.findByProductIdForUpdate(item.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Inventory for product", item.getProductId()));

            if (inventory.getQuantityAvailable() < item.getQuantity()) {
                log.warn("Insufficient stock for product {} on order {}: requested {}, available {}",
                        item.getProductId(), orderId, item.getQuantity(), inventory.getQuantityAvailable());
                throw new InsufficientInventoryException(
                        item.getProductId(), item.getQuantity(), inventory.getQuantityAvailable());
            }

            inventory.setQuantityAvailable(inventory.getQuantityAvailable() - item.getQuantity());
            inventory.setQuantityReserved(inventory.getQuantityReserved() + item.getQuantity());
            inventoryRepository.save(inventory);

            transactionRepository.save(InventoryTransaction.builder()
                    .productId(item.getProductId())
                    .orderId(orderId)
                    .transactionType(InventoryTransaction.TransactionType.RESERVE)
                    .quantity(item.getQuantity())
                    .build());
        }

        String reservationId = UUID.randomUUID().toString();
        log.info("Reserved {} line(s) for order {}", items.size(), orderId);
        return ReservationResponse.builder()
                .orderId(orderId)
                .reservationId(reservationId)
                .reserved(true)
                .message("Inventory reserved")
                .build();
    }

    /**
     * Compensating action for a failed payment or a cancelled order: returns reserved
     * stock to the available pool.
     *
     * <p>Idempotent for the same reason reserve is - the release message can be
     * redelivered by Kafka.</p>
     */
    @Transactional
    public ReservationResponse release(String orderId) {
        if (transactionRepository.existsByOrderIdAndTransactionType(
                orderId, InventoryTransaction.TransactionType.RELEASE)) {
            log.info("Inventory for order {} already released - ignoring duplicate", orderId);
            return ReservationResponse.builder()
                    .orderId(orderId).reserved(false).message("Already released").build();
        }

        List<InventoryTransaction> reservations = transactionRepository.findByOrderId(orderId).stream()
                .filter(t -> t.getTransactionType() == InventoryTransaction.TransactionType.RESERVE)
                .toList();

        if (reservations.isEmpty()) {
            log.warn("No reservation found to release for order {}", orderId);
            return ReservationResponse.builder()
                    .orderId(orderId).reserved(false).message("Nothing to release").build();
        }

        for (InventoryTransaction reservation : reservations) {
            Inventory inventory = inventoryRepository.findByProductIdForUpdate(reservation.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Inventory for product", reservation.getProductId()));

            inventory.setQuantityAvailable(inventory.getQuantityAvailable() + reservation.getQuantity());
            inventory.setQuantityReserved(
                    Math.max(0, inventory.getQuantityReserved() - reservation.getQuantity()));
            inventoryRepository.save(inventory);

            transactionRepository.save(InventoryTransaction.builder()
                    .productId(reservation.getProductId())
                    .orderId(orderId)
                    .transactionType(InventoryTransaction.TransactionType.RELEASE)
                    .quantity(reservation.getQuantity())
                    .build());
        }

        log.info("Released inventory for order {}", orderId);
        return ReservationResponse.builder()
                .orderId(orderId).reserved(false).message("Inventory released").build();
    }

    /**
     * Confirms a reservation after successful payment: the held quantity leaves the
     * reserved pool for good rather than returning to available.
     */
    @Transactional
    public void confirm(String orderId) {
        if (transactionRepository.existsByOrderIdAndTransactionType(
                orderId, InventoryTransaction.TransactionType.CONFIRM)) {
            return;
        }
        List<InventoryTransaction> reservations = transactionRepository.findByOrderId(orderId).stream()
                .filter(t -> t.getTransactionType() == InventoryTransaction.TransactionType.RESERVE)
                .toList();

        for (InventoryTransaction reservation : reservations) {
            inventoryRepository.findByProductIdForUpdate(reservation.getProductId()).ifPresent(inventory -> {
                inventory.setQuantityReserved(
                        Math.max(0, inventory.getQuantityReserved() - reservation.getQuantity()));
                inventoryRepository.save(inventory);
            });
            transactionRepository.save(InventoryTransaction.builder()
                    .productId(reservation.getProductId())
                    .orderId(orderId)
                    .transactionType(InventoryTransaction.TransactionType.CONFIRM)
                    .quantity(reservation.getQuantity())
                    .build());
        }
        log.info("Confirmed inventory consumption for order {}", orderId);
    }

    @Transactional
    public InventoryResponse restock(String productId, int quantity) {
        Inventory inventory = inventoryRepository.findByProductIdForUpdate(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory for product", productId));
        inventory.setQuantityAvailable(inventory.getQuantityAvailable() + quantity);
        inventoryRepository.save(inventory);

        transactionRepository.save(InventoryTransaction.builder()
                .productId(productId)
                .transactionType(InventoryTransaction.TransactionType.RESTOCK)
                .quantity(quantity)
                .build());
        return InventoryResponse.from(inventory);
    }
}
