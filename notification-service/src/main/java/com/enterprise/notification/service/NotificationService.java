package com.enterprise.notification.service;

import com.enterprise.common.dto.PageResponse;
import com.enterprise.notification.dto.NotificationResponse;
import com.enterprise.notification.entity.Notification;
import com.enterprise.notification.repository.NotificationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class NotificationService {

    private final NotificationRepository repository;

    public NotificationService(NotificationRepository repository) {
        this.repository = repository;
    }

    /**
     * Records a notification and "delivers" it.
     *
     * <p>Delivery is logged rather than sent: wiring a real SMTP or SMS provider would
     * make cloning and running this project depend on third-party credentials. The
     * boundary is here, so swapping in a provider touches this method only.</p>
     */
    @Transactional
    public void record(String eventId, String customerId, String orderId,
                       String eventType, String subject, String body) {
        if (eventId != null && repository.existsByEventId(eventId)) {
            log.info("Notification for event {} already recorded - skipping duplicate", eventId);
            return;
        }

        Notification notification = repository.save(Notification.builder()
                .eventId(eventId)
                .customerId(customerId)
                .orderId(orderId)
                .eventType(eventType)
                .subject(subject)
                .body(body)
                .channel(Notification.Channel.EMAIL)
                .deliveryStatus(Notification.DeliveryStatus.SENT)
                .build());

        log.info("NOTIFICATION [{}] to customer {} | {} | {}",
                notification.getChannel(), customerId, subject, body);
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> findByCustomer(String customerId, Pageable pageable) {
        return PageResponse.from(repository
                .findByCustomerIdOrderByCreatedAtDesc(customerId, pageable)
                .map(NotificationResponse::from));
    }
}
