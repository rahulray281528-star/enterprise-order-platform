package com.enterprise.order.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Mirrors inventory-service's ReservationRequest across the service boundary. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryReserveRequest {

    private String orderId;
    private List<Line> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Line {
        private String productId;
        private Integer quantity;
    }
}
