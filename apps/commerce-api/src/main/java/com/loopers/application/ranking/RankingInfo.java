package com.loopers.application.ranking;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.common.PageResult;
import com.loopers.domain.product.Product;
import com.loopers.domain.ranking.PeriodRange;
import com.loopers.domain.ranking.RankingPeriod;

public final class RankingInfo {

    private RankingInfo() {
    }

    /** 조회 기간(확정된 시작·종료일)과 조립된 페이지를 함께 담는다 — 기간 확정은 응용의 책임. */
    public record PeriodResult(
            RankingPeriod period,
            PeriodRange range,
            PageResult<RankedItem> result
    ) {
    }

    /** 랭킹 페이지의 한 항목 — 상품 ID 가 아닌 상품 정보로 조립(aggregation)해 반환한다. */
    public record RankedItem(
            long rank,
            Long productId,
            Long brandId,
            String brandName,
            String name,
            long price,
            long likeCount,
            double score
    ) {

        public static RankedItem from(long rank, double score, Product product, Brand brand) {
            return new RankedItem(
                    rank,
                    product.getId(),
                    brand.getId(),
                    brand.getName(),
                    product.getName(),
                    product.getPrice().getAmount(),
                    product.getLikeCount(),
                    score
            );
        }
    }
}
