package com.enterprise.order.service;

import com.enterprise.common.constant.KafkaTopics;
import com.enterprise.common.constant.OrderStatus;
import com.enterprise.common.dto.PageResponse;
import com.enterprise.common.event.OrderCancelledEvent;
import com.enterprise.common.event.OrderConfirmedEvent;
import com.enterprise.common.event.OrderCreatedEvent;
import com.enterprise.common.event.OrderItemPayload;
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
import com.enterprise.order.entity.OrderItem;
import com.enterprise.order.entity.OrderStatusHistory;
import com.enterprise.order.messaging.OutboundEvent;
import com.enterprise.order.repository.OrderRepository;
import com.enterprise.order.repository.OrderStatusHistoryRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates the order lifecycle.
 *
 * <p>Order creation is a saga, not a distributed transaction: the local order row and
 * the remote inventory reservation cannot share an ACID boundary. Each step is therefore
 * paired with a compensating action, and every state change is recorded so the current
 * position in the saga is always reconstructible from the database.</p>
 */
@Service
@Slf4j
public class OrderService {

    /** Statuses from which a customer is still allowed to cancel. */
    private static final Set<OrderStatus> CANCELLABLE = EnumSet.of(
            OrderStatus.CREATED, OrderStatus.INVENTORY_RESERVED, OrderStatus.PAYMENT_PENDING);

    private static final DateTimeFormatter ORDER_NUMBER_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final ProductClient productClient;
    private final InventoryClientFacade inventoryClient;
    private final ApplicationEventPublisher eventPublisher;

    public OrderService(OrderRepository orderRepository,
                        OrderStatusHistoryRepository historyRepository,
                        ProductClient productClient,
                        InventoryClientFacade inventoryClient,
                        ApplicationEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.historyRepository = historyRepository;
        this.productClient = productClient;
        this.inventoryClient = inventoryClient;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public OrderResponse create(String customerId, CreateOrderRequest request) {
        log.info("Creating order for customer {} with {} line(s)", customerId, request.getItems().size());

        Order order = Order.builder()
                .orderNumber(generateOrderNumber())
                .customerId(customerId)
                .status(OrderStatus.CREATED)
                .totalAmount(BigDecimal.ZERO)
                .build();

        BigDecimal total = BigDecimal.ZERO;
        for (OrderItemRequest line : request.getItems()) {
            ProductSnapshot.Payload product = loadProduct(line.getProductId());

            BigDecimal lineTotal = product.getPrice().multiply(BigDecimal.valueOf(line.getQuantity()));
            order.addItem(OrderItem.builder()
                    .productId(product.getId())
                    .productName(product.getName())
                    .quantity(line.getQuantity())
                    // Price is copied onto the order, not referenced, so a later
                    // catalogue change cannot alter what the customer agreed to pay.
                    .unitPrice(product.getPrice())
                    .lineTotal(lineTotal)
                    .build());
            total = total.add(lineTotal);
        }

        order.setTotalAmount(total);
        Order saved = orderRepository.save(order);
        recordTransition(saved, null, OrderStatus.CREATED, "Order created");

        // Synchronous: the customer must get an immediate answer on availability.
        // Throws (and rolls the whole order back) when stock is short or inventory is down.
        inventoryClient.reserve(InventoryReserveRequest.builder()
                .orderId(saved.getId())
                .items(saved.getItems().stream()
                        .map(i -> InventoryReserveRequest.Line.builder()
                                .productId(i.getProductId())
                                .quantity(i.getQuantity())
                                .build())
                        .toList())
                .build());

        transitionTo(saved, OrderStatus.INVENTORY_RESERVED, "Inventory reserved");
        transitionTo(saved, OrderStatus.PAYMENT_PENDING, "Awaiting payment");
        orderRepository.save(saved);

        // Handed to Kafka only after this transaction commits.
        eventPublisher.publishEvent(new OutboundEvent(KafkaTopics.ORDER_CREATED, saved.getId(),
                OrderCreatedEvent.builder()
                        .orderId(saved.getId())
                        .customerId(saved.getCustomerId())
                        .totalAmount(saved.getTotalAmount())
                        .items(saved.getItems().stream()
                                .map(i -> OrderItemPayload.builder()
                                        .productId(i.getProductId())
                                        .productName(i.getProductName())
                                        .quantity(i.getQuantity())
                                        .unitPrice(i.getUnitPrice())
                                        .build())
                                .toList())
                        .build()));

        log.info("Order {} created for customer {}, total {}",
                saved.getOrderNumber(), customerId, saved.getTotalAmount());
        return OrderResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public OrderResponse getById(String orderId, String requesterId, boolean isAdmin) {
        Order order = orderRepository.findWithItemsById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
        if (!isAdmin && !order.getCustomerId().equals(requesterId)) {
            throw new ForbiddenException("You may only view your own orders");
        }
        return OrderResponse.from(order);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> findByCustomer(String customerId, OrderStatus status, Pageable pageable) {
        Page<Order> page = status == null
                ? orderRepository.findByCustomerId(customerId, pageable)
                : orderRepository.findByCustomerIdAndStatus(customerId, status, pageable);
        return PageResponse.from(page.map(OrderResponse::summaryFrom));
    }

    @Transactional
    public OrderResponse cancel(String orderId, String requesterId, boolean isAdmin) {
        Order order = orderRepository.findWithItemsById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

        if (!isAdmin && !order.getCustomerId().equals(requesterId)) {
            throw new ForbiddenException("You may only cancel your own orders");
        }
        if (!CANCELLABLE.contains(order.getStatus())) {
            throw new InvalidOrderException(
                    "Order in status " + order.getStatus() + " can no longer be cancelled");
        }

        transitionTo(order, OrderStatus.CANCELLED, "Cancelled by " + (isAdmin ? "admin" : "customer"));
        orderRepository.save(order);

        // inventory-service consumes this and returns the reserved stock.
        eventPublisher.publishEvent(new OutboundEvent(KafkaTopics.ORDER_CANCELLED, order.getId(),
                OrderCancelledEvent.builder()
                        .orderId(order.getId())
                        .customerId(order.getCustomerId())
                        .reason("Cancelled by " + (isAdmin ? "admin" : "customer"))
                        .build()));

        return OrderResponse.from(order);
    }

    /** Called by the payment-completed consumer. */
    @Transactional
    public void markConfirmed(String orderId, String paymentId) {
        Order order = orderRepository.findWithItemsById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

        if (order.getStatus() == OrderStatus.CONFIRMED) {
            log.info("Order {} already confirmed - ignoring duplicate", orderId);
            return;
        }

        order.setPaymentId(paymentId);
        transitionTo(order, OrderStatus.CONFIRMED, "Payment completed");
        orderRepository.save(order);

        eventPublisher.publishEvent(new OutboundEvent(KafkaTopics.ORDER_CONFIRMED, order.getId(),
                OrderConfirmedEvent.builder()
                        .orderId(order.getId())
                        .customerId(order.getCustomerId())
                        .paymentId(paymentId)
                        .totalAmount(order.getTotalAmount())
                        .build()));
    }

    /** Called by the payment-failed consumer. Inventory release is driven by the same event. */
    @Transactional
    public void markPaymentFailed(String orderId, String reason) {
        Order order = orderRepository.findWithItemsById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

        if (order.getStatus() == OrderStatus.PAYMENT_FAILED || order.getStatus() == OrderStatus.CANCELLED) {
            return;
        }

        order.setFailureReason(reason);
        transitionTo(order, OrderStatus.PAYMENT_FAILED, "Payment failed: " + reason);
        orderRepository.save(order);
    }

    private ProductSnapshot.Payload loadProduct(String productId) {
        ProductSnapshot snapshot = productClient.getById(productId);
        if (snapshot == null || snapshot.getData() == null) {
            throw new ResourceNotFoundException("Product", productId);
        }
        ProductSnapshot.Payload product = snapshot.getData();
        if (Boolean.FALSE.equals(product.getIsActive())) {
            throw new InvalidOrderException("Product is no longer available: " + product.getSku());
        }
        if (product.getPrice() == null || product.getPrice().signum() <= 0) {
            throw new InvalidOrderException("Product has no valid price: " + productId);
        }
        return product;
    }

    private void transitionTo(Order order, OrderStatus target, String note) {
        OrderStatus from = order.getStatus();
        order.setStatus(target);
        recordTransition(order, from, target, note);
    }

    private void recordTransition(Order order, OrderStatus from, OrderStatus to, String note) {
        historyRepository.save(OrderStatusHistory.builder()
                .orderId(order.getId())
                .fromStatus(from)
                .toStatus(to)
                .note(note)
                .build());
        log.debug("Order {} transitioned {} -> {}", order.getId(), from, to);
    }

    private String generateOrderNumber() {
        return "ORD-" + LocalDate.now().format(ORDER_NUMBER_DATE)
                + "-" + ThreadLocalRandom.current().nextInt(100_000, 999_999);
    }
}
