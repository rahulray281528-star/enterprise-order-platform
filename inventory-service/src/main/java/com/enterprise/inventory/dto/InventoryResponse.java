package com.enterprise.inventory.dto;

import com.enterprise.inventory.entity.Inventory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryResponse {

    private String productId;
    private String sku;
    private Integer quantityAvailable;
    private Integer quantityReserved;
    private Integer reorderLevel;
    private boolean belowReorderLevel;

    public static InventoryResponse from(Inventory inventory) {
        return InventoryResponse.builder()
                .productId(inventory.getProductId())
                .sku(inventory.getSku())
                .quantityAvailable(inventory.getQuantityAvailable())
                .quantityReserved(inventory.getQuantityReserved())
                .reorderLevel(inventory.getReorderLevel())
                .belowReorderLevel(inventory.getQuantityAvailable() <= inventory.getReorderLevel())
                .build();
    }
}
