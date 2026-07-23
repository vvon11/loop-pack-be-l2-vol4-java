package com.loopers.batch.domain.ranking;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/**
 * batch 에는 DAILY Job 이 없으므로 WEEKLY/MONTHLY 만 정의한다.
 * api 의 {@code RankingPeriod} 와는 의도적으로 별개 사본 — RankingKeys 가 api/streamer 에
 * 각각 존재하는 기존 컨벤션과 동일하게, 두 모듈은 서로 의존하지 않는다.
 */
public enum RankingPeriodType {
    WEEKLY("mv_product_rank_weekly"),
    MONTHLY("mv_product_rank_monthly");

    private final String tableName;

    RankingPeriodType(String tableName) {
        this.tableName = tableName;
    }

    public String tableName() {
        return tableName;
    }

    public PeriodRange rangeOf(LocalDate targetDate) {
        return switch (this) {
            case WEEKLY -> {
                LocalDate start = targetDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                yield new PeriodRange(start, start.plusDays(6));
            }
            case MONTHLY -> {
                LocalDate start = targetDate.withDayOfMonth(1);
                yield new PeriodRange(start, targetDate.with(TemporalAdjusters.lastDayOfMonth()));
            }
        };
    }
}
