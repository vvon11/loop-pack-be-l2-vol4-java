package com.loopers.batch.domain.ranking;

import java.math.BigDecimal;

/** 집계 SQL 한 행 = 기간 내 한 상품의 합산 지표와 최종 순위. */
public record ProductRankAggregate(
        long productId,
        long rankNo,
        long viewCount,
        long likeCount,
        long salesCount,
        BigDecimal score
) {
}
