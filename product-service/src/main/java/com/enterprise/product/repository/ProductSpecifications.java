package com.enterprise.product.repository;

import com.enterprise.product.entity.Product;
import java.math.BigDecimal;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

/**
 * Composable predicates for the product listing endpoint.
 *
 * <p>Each returns null when its filter is absent; Spring Data drops null specifications
 * when combining, so one query serves every combination of filters without duplicating
 * repository methods per permutation.</p>
 */
public final class ProductSpecifications {

    private ProductSpecifications() {
    }

    public static Specification<Product> inCategory(String categoryId) {
        if (!StringUtils.hasText(categoryId)) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("category").get("id"), categoryId);
    }

    public static Specification<Product> nameContains(String search) {
        if (!StringUtils.hasText(search)) {
            return null;
        }
        String pattern = "%" + search.toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("name")), pattern);
    }

    public static Specification<Product> priceAtLeast(BigDecimal minPrice) {
        if (minPrice == null) {
            return null;
        }
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("price"), minPrice);
    }

    public static Specification<Product> priceAtMost(BigDecimal maxPrice) {
        if (maxPrice == null) {
            return null;
        }
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("price"), maxPrice);
    }

    public static Specification<Product> activeOnly(boolean activeOnly) {
        if (!activeOnly) {
            return null;
        }
        return (root, query, cb) -> cb.isTrue(root.get("isActive"));
    }
}
