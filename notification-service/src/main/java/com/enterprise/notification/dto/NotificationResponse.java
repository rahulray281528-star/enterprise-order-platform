package com.enterprise.notification.dto;

import com.enterprise.notification.entity.Notification;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {

    private String id;
    private String customerId;
    private String orderId;
    private String eventType;
    private String subject;
    private String body;
    private String channel;
    private String deliveryStatus;
    private LocalDateTime createdAt;

    public static NotificationResponse from(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .customerId(n.getCustomerId())
                .orderId(n.getOrderId())
                .eventType(n.getEventType())
                .subject(n.getSubject())
                .body(n.getBody())
                .channel(n.getChannel().name())
                .deliveryStatus(n.getDeliveryStatus().name())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
