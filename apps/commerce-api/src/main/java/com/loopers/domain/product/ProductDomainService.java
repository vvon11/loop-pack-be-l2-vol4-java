package com.loopers.domain.product;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.common.PageResult;
import com.loopers.domain.like.LikeRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class ProductDomainService {

    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;
    private final LikeRepository likeRepository;

    public ProductDetail getDetail(Long productId) {
        Product product = productRepository.find(productId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));
        Brand brand = brandRepository.find(product.getBrandId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다."));
        long likeCount = likeRepository.countByProductId(productId);
        return new ProductDetail(product, brand.getId(), brand.getName(), likeCount);
    }

    public PageResult<ProductDetail> getList(ProductCommand.Search search) {
        PageResult<Product> products = productRepository.findAll(search);
        if (products.content().isEmpty()) {
            return new PageResult<>(
                    List.of(),
                    products.page(),
                    products.size(),
                    products.hasNext(),
                    products.totalElements()
            );
        }

        Set<Long> brandIds = products.content().stream()
                .map(Product::getBrandId)
                .collect(Collectors.toSet());
        Map<Long, Brand> brandById = brandRepository.findAllByIds(brandIds).stream()
                .collect(Collectors.toMap(Brand::getId, Function.identity()));

        Set<Long> productIds = products.content().stream()
                .map(Product::getId)
                .collect(Collectors.toSet());
        Map<Long, Long> likeCountByProductId = likeRepository.countByProductIds(productIds);

        List<ProductDetail> details = products.content().stream()
                .map(p -> {
                    Brand brand = brandById.get(p.getBrandId());
                    if (brand == null) {
                        throw new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다.");
                    }
                    return new ProductDetail(
                            p,
                            brand.getId(),
                            brand.getName(),
                            likeCountByProductId.getOrDefault(p.getId(), 0L)
                    );
                })
                .toList();

        return new PageResult<>(
                details,
                products.page(),
                products.size(),
                products.hasNext(),
                products.totalElements()
        );
    }
}
