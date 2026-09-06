package com.enterprise.inventory.repository;

import com.enterprise.inventory.entity.InventoryTransaction;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, String> {

    List<InventoryTransaction> findByOrderId(String orderId);

    boolean existsByOrderIdAndTransactionType(String orderId,
                                              InventoryTransaction.TransactionType transactionType);
}
