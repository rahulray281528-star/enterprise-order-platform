package com.enterprise.product.service;

import com.enterprise.common.exception.BusinessException;
import com.enterprise.common.exception.ResourceNotFoundException;
import com.enterprise.product.config.CacheConfig;
import com.enterprise.product.dto.CategoryRequest;
import com.enterprise.product.dto.CategoryResponse;
import com.enterprise.product.entity.Category;
import com.enterprise.product.repository.CategoryRepository;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@Transactional
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Cacheable(cacheNames = CacheConfig.CATEGORIES_CACHE, key = "'all'")
    @Transactional(readOnly = true)
    public List<CategoryResponse> findAll() {
        return categoryRepository.findAll().stream().map(CategoryResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse getById(String id) {
        return categoryRepository.findById(id).map(CategoryResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Category", id));
    }

    @CacheEvict(cacheNames = CacheConfig.CATEGORIES_CACHE, allEntries = true)
    public CategoryResponse create(CategoryRequest request) {
        if (categoryRepository.existsByName(request.getName())) {
            throw new BusinessException("Category already exists: " + request.getName(),
                    "CATEGORY_EXISTS", 409);
        }
        Category saved = categoryRepository.save(Category.builder()
                .name(request.getName())
                .description(request.getDescription())
                .build());
        log.info("Created category {}", saved.getId());
        return CategoryResponse.from(saved);
    }
}
