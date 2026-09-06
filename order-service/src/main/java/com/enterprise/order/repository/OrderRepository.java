package com.enterprise.order.repository;

import com.enterprise.common.constant.OrderStatus;
import com.enterprise.order.entity.Order;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderRepository extends JpaRepository<Order, String> {

    /** Fetches items in the same query to avoid the N+1 that a lazy list would cause. */
    @EntityGraph(attributePaths = "items")
    Optional<Order> findWithItemsById(String id);

    Optional<Order> findByOrderNumber(String orderNumber);

    Page<Order> findByCustomerId(String customerId, Pageable pageable);

    Page<Order> findByCustomerIdAndStatus(String customerId, OrderStatus status, Pageable pageable);
}
