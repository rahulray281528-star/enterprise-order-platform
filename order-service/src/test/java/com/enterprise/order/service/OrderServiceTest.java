package com.enterprise.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.common.constant.OrderStatus;
import com.enterprise.common.exception.ForbiddenException;
import com.enterprise.common.exception.InvalidOrderException;
import com.enterprise.common.exception.ResourceNotFoundException;
import com.enterprise.order.client.InventoryClientFacade;
import com.enterprise.order.client.ProductClient;
import com.enterprise.order.dto.CreateOrderRequest;
import com.enterprise.order.dto.InventoryReserveRequest;
import com.enterprise.order.dto.OrderItemRequest;
import com.enterprise.order.dto.OrderResponse;
import com.enterprise.order.dto.ProductSnapshot;
import com.enterprise.order.entity.Order;
import com.enterprise.order.messaging.OutboundEvent;
import com.enterprise.order.repository.OrderRepository;
import com.enterprise.order.repository.OrderStatusHistoryRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderStatusHistoryRepository historyRepository;

    @Mock
    private ProductClient productClient;

    @Mock
    private InventoryClientFacade inventoryClient;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private OrderService orderService;

    private ProductSnapshot snapshot(String id, String price, boolean active) {
        return ProductSnapshot.builder()
                .success(true)
                .data(ProductSnapshot.Payload.builder()
                        .id(id).sku("SKU-" + id).name("Product " + id)
                        .price(new BigDecimal(price)).isActive(active).build())
                .build();
    }

    private CreateOrderRequest orderFor(String productId, int quantity) {
        return CreateOrderRequest.builder()
                .items(List.of(OrderItemRequest.builder()
                        .productId(productId).quantity(quantity).build()))
                .build();
    }

    @Test
    @DisplayName("creating an order prices each line, totals them and reserves inventory")
    void createPricesAndReserves() {
        when(productClient.getById("prod-1")).thenReturn(snapshot("prod-1", "250.50", true));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.create("cust-1", orderFor("prod-1", 4));

        // 250.50 x 4 = 1002.00
        assertThat(response.getTotalAmount()).isEqualByComparingTo("1002.00");
        assertThat(response.getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
        assertThat(response.getOrderNumber()).startsWith("ORD-");
        verify(inventoryClient).reserve(any(InventoryReserveRequest.class));
    }

    @Test
    @DisplayName("OrderCreatedEvent is published so payment can pick the order up")
    void createPublishesOrderCreated() {
        when(productClient.getById("prod-1")).thenReturn(snapshot("prod-1", "100.00", true));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        orderService.create("cust-1", orderFor("prod-1", 1));

        ArgumentCaptor<OutboundEvent> captor = ArgumentCaptor.forClass(OutboundEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().topic()).isEqualTo("order-created");
    }

    @Test
    @DisplayName("an inactive product cannot be ordered and no inventory is reserved")
    void createRejectsInactiveProduct() {
        when(productClient.getById("prod-1")).thenReturn(snapshot("prod-1", "100.00", false));

        assertThatThrownBy(() -> orderService.create("cust-1", orderFor("prod-1", 1)))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessageContaining("no longer available");

        verify(inventoryClient, never()).reserve(any());
    }

    @Test
    @DisplayName("an unknown product id is a 404, not a 500")
    void createRejectsUnknownProduct() {
        when(productClient.getById("ghost")).thenReturn(ProductSnapshot.builder().success(false).build());

        assertThatThrownBy(() -> orderService.create("cust-1", orderFor("ghost", 1)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("a confirmed order can no longer be cancelled")
    void cancelRejectsConfirmedOrder() {
        Order order = Order.builder()
                .id("order-1").orderNumber("ORD-1").customerId("cust-1")
                .status(OrderStatus.CONFIRMED).totalAmount(new BigDecimal("100.00")).build();
        when(orderRepository.findWithItemsById("order-1")).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancel("order-1", "cust-1", false))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessageContaining("can no longer be cancelled");
    }

    @Test
    @DisplayName("a customer cannot cancel somebody else's order")
    void cancelRejectsForeignOrder() {
        Order order = Order.builder()
                .id("order-1").customerId("cust-1")
                .status(OrderStatus.PAYMENT_PENDING).totalAmount(BigDecimal.TEN).build();
        when(orderRepository.findWithItemsById("order-1")).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancel("order-1", "cust-2", false))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("cancelling publishes OrderCancelledEvent so inventory releases the stock")
    void cancelPublishesEvent() {
        Order order = Order.builder()
                .id("order-1").orderNumber("ORD-1").customerId("cust-1")
                .status(OrderStatus.PAYMENT_PENDING).totalAmount(BigDecimal.TEN).build();
        when(orderRepository.findWithItemsById("order-1")).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.cancel("order-1", "cust-1", false);

        assertThat(response.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        ArgumentCaptor<OutboundEvent> captor = ArgumentCaptor.forClass(OutboundEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().topic()).isEqualTo("order-cancelled");
    }

    @Test
    @DisplayName("a duplicate PaymentCompleted delivery does not confirm the order twice")
    void markConfirmedIsIdempotent() {
        Order order = Order.builder()
                .id("order-1").customerId("cust-1")
                .status(OrderStatus.CONFIRMED).totalAmount(BigDecimal.TEN).build();
        when(orderRepository.findWithItemsById("order-1")).thenReturn(Optional.of(order));

        orderService.markConfirmed("order-1", "pay-1");

        verify(orderRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(OutboundEvent.class));
    }
}
