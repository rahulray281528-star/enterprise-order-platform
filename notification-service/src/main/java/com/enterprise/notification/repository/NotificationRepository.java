package com.enterprise.notification.repository;

import com.enterprise.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, String> {

    Page<Notification> findByCustomerIdOrderByCreatedAtDesc(String customerId, Pageable pageable);

    boolean existsByEventId(String eventId);
}
