package com.loopers.domain.product;

public final class ProductCommand {

    private ProductCommand() {
    }

    public record Search(int page, int size, Long brandId, ProductSortType sort) {

        public Search {
            if (page < 0) {
                throw new IllegalArgumentException("page 는 0 이상이어야 합니다.");
            }
            if (size <= 0) {
                throw new IllegalArgumentException("size 는 1 이상이어야 합니다.");
            }
            if (sort == null) {
                sort = ProductSortType.LATEST;
            }
        }
    }
}
