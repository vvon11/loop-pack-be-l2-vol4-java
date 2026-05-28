package com.loopers.domain.product;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.common.PageResult;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductDomainServiceTest {

    private final ProductDomainService productDomainService = new ProductDomainService();

    private static Product product(long id, long brandId, long price) {
        return Product.restore(id, brandId, "상품" + id, Money.of(price), Stock.of(10));
    }

    private static Brand brand(long id, String name) {
        return Brand.restore(id, name, null);
    }

    @DisplayName("ProductDomainService.getDetail 은 ")
    @Nested
    class GetDetail {

        @DisplayName("상품/브랜드/좋아요 수를 받아 ProductDetail 합성체로 반환한다.")
        @Test
        void composesDetail() {
            Product product = product(101L, 1L, 1_000L);
            Brand brand = brand(1L, "브랜드A");

            ProductDetail detail = productDomainService.getDetail(product, brand, 7L);

            assertThat(detail.product()).isSameAs(product);
            assertThat(detail.brandId()).isEqualTo(1L);
            assertThat(detail.brandName()).isEqualTo("브랜드A");
            assertThat(detail.likeCount()).isEqualTo(7L);
        }

        @DisplayName("product 가 null 이면 NOT_FOUND.")
        @Test
        void throwsNotFound_whenProductNull() {
            CoreException result = assertThrows(CoreException.class,
                    () -> productDomainService.getDetail(null, brand(1L, "B"), 0L));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("brand 가 null 이면 NOT_FOUND.")
        @Test
        void throwsNotFound_whenBrandNull() {
            CoreException result = assertThrows(CoreException.class,
                    () -> productDomainService.getDetail(product(101L, 1L, 1_000L), null, 0L));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("ProductDomainService.composeList 는 ")
    @Nested
    class ComposeList {

        @DisplayName("상품 페이지 + 브랜드 맵 + 좋아요 수 맵을 받아 ProductDetail 페이지로 합성한다 (likeCount 누락은 0 fallback).")
        @Test
        void composesList() {
            // arrange — 상품 3개 (브랜드 1,1,2). likeCount 는 101=5, 103=2 만 있음 — 102 는 0 fallback
            Product p1 = product(101L, 1L, 1_000L);
            Product p2 = product(102L, 1L, 2_000L);
            Product p3 = product(103L, 2L, 3_000L);
            PageResult<Product> products = new PageResult<>(List.of(p1, p2, p3), 0, 20, false, 3);
            Map<Long, Brand> brandById = Map.of(
                    1L, brand(1L, "브랜드A"),
                    2L, brand(2L, "브랜드B")
            );
            Map<Long, Long> likeCountByProductId = Map.of(101L, 5L, 103L, 2L);

            // act
            PageResult<ProductDetail> result = productDomainService.composeList(products, brandById, likeCountByProductId);

            // assert
            assertThat(result.content()).hasSize(3);
            assertThat(result.content())
                    .extracting(ProductDetail::likeCount)
                    .containsExactly(5L, 0L, 2L);
            assertThat(result.content())
                    .extracting(ProductDetail::brandName)
                    .containsExactly("브랜드A", "브랜드A", "브랜드B");
            assertThat(result.totalElements()).isEqualTo(3L);
        }

        @DisplayName("상품 페이지가 비어있으면 빈 PageResult 를 그대로 반환한다.")
        @Test
        void returnsEmptyPage_whenContentEmpty() {
            PageResult<Product> empty = new PageResult<>(List.of(), 0, 20, false, 0);

            PageResult<ProductDetail> result = productDomainService.composeList(empty, Map.of(), Map.of());

            assertThat(result.content()).isEmpty();
            assertThat(result.totalElements()).isZero();
        }

        @DisplayName("brandById 에 상품의 brandId 가 누락되면 NOT_FOUND.")
        @Test
        void throwsNotFound_whenBrandMissingForProduct() {
            Product p = product(101L, 999L, 1_000L);
            PageResult<Product> products = new PageResult<>(List.of(p), 0, 20, false, 1);

            CoreException result = assertThrows(CoreException.class,
                    () -> productDomainService.composeList(products, Map.of(), Map.of()));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
