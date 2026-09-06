package com.enterprise.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.common.exception.InsufficientInventoryException;
import com.enterprise.inventory.dto.ReservationItem;
import com.enterprise.inventory.dto.ReservationRequest;
import com.enterprise.inventory.dto.ReservationResponse;
import com.enterprise.inventory.entity.Inventory;
import com.enterprise.inventory.entity.InventoryTransaction;
import com.enterprise.inventory.repository.InventoryRepository;
import com.enterprise.inventory.repository.InventoryTransactionRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private InventoryTransactionRepository transactionRepository;

    @InjectMocks
    private InventoryService inventoryService;

    private Inventory stock(String productId, int available, int reserved) {
        return Inventory.builder()
                .id("inv-" + productId).productId(productId).sku("SKU-" + productId)
                .quantityAvailable(available).quantityReserved(reserved).reorderLevel(5)
                .build();
    }

    private ReservationRequest request(String orderId, String productId, int quantity) {
        return ReservationRequest.builder()
                .orderId(orderId)
                .items(List.of(ReservationItem.builder().productId(productId).quantity(quantity).build()))
                .build();
    }

    @Test
    @DisplayName("reserving moves quantity from available to reserved and records an audit row")
    void reserveMovesQuantity() {
        Inventory inventory = stock("prod-1", 10, 0);
        when(transactionRepository.existsByOrderIdAndTransactionType(
                "order-1", InventoryTransaction.TransactionType.RESERVE)).thenReturn(false);
        when(inventoryRepository.findByProductIdForUpdate("prod-1")).thenReturn(Optional.of(inventory));

        ReservationResponse response = inventoryService.reserve(request("order-1", "prod-1", 3));

        assertThat(response.isReserved()).isTrue();
        assertThat(inventory.getQuantityAvailable()).isEqualTo(7);
        assertThat(inventory.getQuantityReserved()).isEqualTo(3);
        verify(transactionRepository).save(any(InventoryTransaction.class));
    }

    @Test
    @DisplayName("reserving more than is available fails and leaves stock untouched")
    void reserveRejectsInsufficientStock() {
        Inventory inventory = stock("prod-1", 2, 0);
        when(transactionRepository.existsByOrderIdAndTransactionType(
                "order-1", InventoryTransaction.TransactionType.RESERVE)).thenReturn(false);
        when(inventoryRepository.findByProductIdForUpdate("prod-1")).thenReturn(Optional.of(inventory));

        assertThatThrownBy(() -> inventoryService.reserve(request("order-1", "prod-1", 5)))
                .isInstanceOf(InsufficientInventoryException.class)
                .hasMessageContaining("requested 5")
                .hasMessageContaining("available 2");

        assertThat(inventory.getQuantityAvailable()).isEqualTo(2);
        verify(inventoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("a redelivered reserve for the same order does not hold stock twice")
    void reserveIsIdempotent() {
        when(transactionRepository.existsByOrderIdAndTransactionType(
                "order-1", InventoryTransaction.TransactionType.RESERVE)).thenReturn(true);

        ReservationResponse response = inventoryService.reserve(request("order-1", "prod-1", 3));

        assertThat(response.isReserved()).isTrue();
        assertThat(response.getMessage()).isEqualTo("Already reserved");
        verify(inventoryRepository, never()).findByProductIdForUpdate(anyString());
    }

    @Test
    @DisplayName("releasing returns reserved quantity to the available pool")
    void releaseReturnsStock() {
        Inventory inventory = stock("prod-1", 7, 3);
        InventoryTransaction reservation = InventoryTransaction.builder()
                .productId("prod-1").orderId("order-1")
                .transactionType(InventoryTransaction.TransactionType.RESERVE).quantity(3).build();

        when(transactionRepository.existsByOrderIdAndTransactionType(
                "order-1", InventoryTransaction.TransactionType.RELEASE)).thenReturn(false);
        when(transactionRepository.findByOrderId("order-1")).thenReturn(List.of(reservation));
        when(inventoryRepository.findByProductIdForUpdate("prod-1")).thenReturn(Optional.of(inventory));

        inventoryService.release("order-1");

        assertThat(inventory.getQuantityAvailable()).isEqualTo(10);
        assertThat(inventory.getQuantityReserved()).isZero();
    }

    @Test
    @DisplayName("a duplicate release is ignored, so stock is never returned twice")
    void releaseIsIdempotent() {
        when(transactionRepository.existsByOrderIdAndTransactionType(
                eq("order-1"), eq(InventoryTransaction.TransactionType.RELEASE))).thenReturn(true);

        ReservationResponse response = inventoryService.release("order-1");

        assertThat(response.getMessage()).isEqualTo("Already released");
        verify(inventoryRepository, never()).findByProductIdForUpdate(anyString());
    }

    @Test
    @DisplayName("confirming a reservation clears the reserved pool without returning stock")
    void confirmConsumesReservedStock() {
        Inventory inventory = stock("prod-1", 7, 3);
        InventoryTransaction reservation = InventoryTransaction.builder()
                .productId("prod-1").orderId("order-1")
                .transactionType(InventoryTransaction.TransactionType.RESERVE).quantity(3).build();

        when(transactionRepository.existsByOrderIdAndTransactionType(
                "order-1", InventoryTransaction.TransactionType.CONFIRM)).thenReturn(false);
        when(transactionRepository.findByOrderId("order-1")).thenReturn(List.of(reservation));
        when(inventoryRepository.findByProductIdForUpdate("prod-1")).thenReturn(Optional.of(inventory));

        inventoryService.confirm("order-1");

        assertThat(inventory.getQuantityReserved()).isZero();
        assertThat(inventory.getQuantityAvailable()).isEqualTo(7);
    }
}
