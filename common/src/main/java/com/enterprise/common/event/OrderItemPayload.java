package com.enterprise.common.event;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemPayload {

    private String productId;
    private String productName;
    private Integer quantity;
    private BigDecimal unitPrice;
}
