package com.enterprise.product.repository;

import com.enterprise.product.entity.Product;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * Extends JpaSpecificationExecutor so the optional-filter search is expressed with
 * type-safe Criteria predicates (see {@link ProductSpecifications}) rather than a JPQL
 * string full of ":param IS NULL OR ..." branches. The string version compiles fine and
 * then fails at startup when Hibernate cannot infer a parameter's type - the Criteria
 * version cannot express that mistake.
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, String>,
        JpaSpecificationExecutor<Product> {

    Optional<Product> findBySku(String sku);

    boolean existsBySku(String sku);
}
