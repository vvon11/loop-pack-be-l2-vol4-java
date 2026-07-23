package com.loopers.batch.domain.ranking;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;

/** Processor 산출물 — MV 한 행에 필요한 값이 모두 갖춰진 상태(기간 메타 + 계산 시각 포함). */
public record PeriodRankRow(
        LocalDate periodStart,
        LocalDate periodEnd,
        long productId,
        long rankNo,
        long viewCount,
        long likeCount,
        long salesCount,
        BigDecimal score,
        ZonedDateTime calculatedAt
) {
}
