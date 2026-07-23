package com.loopers.domain.ranking;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

public enum RankingPeriod {
    DAILY,
    WEEKLY,
    MONTHLY;

    public static RankingPeriod from(String raw) {
        if (raw == null || raw.isBlank()) {
            return DAILY;
        }

        try {
            return RankingPeriod.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, "지원하지 않는 조회 기간입니다.");
        }
    }

    public PeriodRange rangeOf(LocalDate baseDate) {
        return switch (this) {
            case DAILY -> new PeriodRange(baseDate, baseDate);
            case WEEKLY -> {
                LocalDate start = baseDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                yield new PeriodRange(start, start.plusDays(6));
            }
            case MONTHLY -> {
                LocalDate start = baseDate.withDayOfMonth(1);
                yield new PeriodRange(start, baseDate.with(TemporalAdjusters.lastDayOfMonth()));
            }
        };
    }
}
