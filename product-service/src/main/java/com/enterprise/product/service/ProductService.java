package com.enterprise.product.service;

import com.enterprise.common.dto.PageResponse;
import com.enterprise.common.exception.BusinessException;
import com.enterprise.common.exception.ResourceNotFoundException;
import com.enterprise.product.config.CacheConfig;
import com.enterprise.product.dto.ProductRequest;
import com.enterprise.product.dto.ProductResponse;
import com.enterprise.product.entity.Category;
import com.enterprise.product.entity.Product;
import com.enterprise.product.repository.CategoryRepository;
import com.enterprise.product.repository.ProductRepository;
import com.enterprise.product.repository.ProductSpecifications;
import java.math.BigDecimal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@Transactional
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    public ProductService(ProductRepository productRepository, CategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    /**
     * First call hits PostgreSQL, subsequent calls are served from Redis until the
     * entry is evicted by an update or delete, or the 10 minute TTL expires.
     */
    @Cacheable(cacheNames = CacheConfig.PRODUCTS_CACHE, key = "#productId")
    @Transactional(readOnly = true)
    public ProductResponse getById(String productId) {
        log.debug("Cache miss for product {} - loading from database", productId);
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));
        return ProductResponse.from(product);
    }

    @Transactional(readOnly = true)
    public ProductResponse getBySku(String sku) {
        Product product = productRepository.findBySku(sku)
                .orElseThrow(() -> new ResourceNotFoundException("Product with sku", sku));
        return ProductResponse.from(product);
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> search(String categoryId, String search,
                                                BigDecimal minPrice, BigDecimal maxPrice,
                                                boolean activeOnly, Pageable pageable) {
        Specification<Product> spec = Specification
                .where(ProductSpecifications.inCategory(categoryId))
                .and(ProductSpecifications.nameContains(search))
                .and(ProductSpecifications.priceAtLeast(minPrice))
                .and(ProductSpecifications.priceAtMost(maxPrice))
                .and(ProductSpecifications.activeOnly(activeOnly));

        Page<ProductResponse> page = productRepository.findAll(spec, pageable)
                .map(ProductResponse::from);
        return PageResponse.from(page);
    }

    public ProductResponse create(ProductRequest request) {
        if (productRepository.existsBySku(request.getSku())) {
            throw new BusinessException("SKU already exists: " + request.getSku(), "SKU_EXISTS", 409);
        }
        Product product = Product.builder()
                .sku(request.getSku())
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .category(resolveCategory(request.getCategoryId()))
                .isActive(request.getIsActive() == null || request.getIsActive())
                .build();

        Product saved = productRepository.save(product);
        log.info("Created product {} (sku {})", saved.getId(), saved.getSku());
        return ProductResponse.from(saved);
    }

    /** Evicts the cached entry so a price or name change is visible immediately. */
    @CacheEvict(cacheNames = CacheConfig.PRODUCTS_CACHE, key = "#productId")
    public ProductResponse update(String productId, ProductRequest request) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));

        if (!product.getSku().equals(request.getSku()) && productRepository.existsBySku(request.getSku())) {
            throw new BusinessException("SKU already exists: " + request.getSku(), "SKU_EXISTS", 409);
        }

        product.setSku(request.getSku());
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setCategory(resolveCategory(request.getCategoryId()));
        if (request.getIsActive() != null) {
            product.setIsActive(request.getIsActive());
        }

        Product saved = productRepository.save(product);
        log.info("Updated product {} - cache entry evicted", productId);
        return ProductResponse.from(saved);
    }

    /**
     * Soft delete. Orders reference products historically, so rows are retired rather
     * than removed to keep past orders readable.
     */
    @CacheEvict(cacheNames = CacheConfig.PRODUCTS_CACHE, key = "#productId")
    public void delete(String productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));
        product.setIsActive(false);
        productRepository.save(product);
        log.info("Deactivated product {}", productId);
    }

    private Category resolveCategory(String categoryId) {
        if (categoryId == null || categoryId.isBlank()) {
            return null;
        }
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category", categoryId));
    }
}
