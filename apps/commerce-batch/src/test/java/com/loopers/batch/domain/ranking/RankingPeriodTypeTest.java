package com.loopers.batch.domain.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class RankingPeriodTypeTest {

    @DisplayName("RankingPeriodType.WEEKLY.rangeOf() 은, ")
    @Nested
    class Weekly {

        @DisplayName("기준일이 월요일이면 자기 자신부터 그 주 일요일까지다.")
        @Test
        void baseDateIsMonday() {
            // arrange
            LocalDate monday = LocalDate.of(2026, 7, 20);

            // act
            PeriodRange range = RankingPeriodType.WEEKLY.rangeOf(monday);

            // assert
            assertThat(range).isEqualTo(new PeriodRange(monday, LocalDate.of(2026, 7, 26)));
        }

        @DisplayName("기준일이 일요일이면 그 주 월요일부터 자기 자신까지다.")
        @Test
        void baseDateIsSunday() {
            // arrange
            LocalDate sunday = LocalDate.of(2026, 7, 26);

            // act
            PeriodRange range = RankingPeriodType.WEEKLY.rangeOf(sunday);

            // assert
            assertThat(range).isEqualTo(new PeriodRange(LocalDate.of(2026, 7, 20), sunday));
        }

        @DisplayName("연도가 바뀌는 주도 올바르게 계산한다.")
        @Test
        void crossesYearBoundary() {
            // arrange
            LocalDate baseDate = LocalDate.of(2026, 1, 1);

            // act
            PeriodRange range = RankingPeriodType.WEEKLY.rangeOf(baseDate);

            // assert
            assertThat(range).isEqualTo(
                new PeriodRange(LocalDate.of(2025, 12, 29), LocalDate.of(2026, 1, 4))
            );
        }
    }

    @DisplayName("RankingPeriodType.MONTHLY.rangeOf() 은, ")
    @Nested
    class Monthly {

        @DisplayName("윤년 2월이면 1일부터 29일까지다.")
        @Test
        void leapFebruary() {
            // arrange
            LocalDate baseDate = LocalDate.of(2024, 2, 15);

            // act
            PeriodRange range = RankingPeriodType.MONTHLY.rangeOf(baseDate);

            // assert
            assertThat(range).isEqualTo(
                new PeriodRange(LocalDate.of(2024, 2, 1), LocalDate.of(2024, 2, 29))
            );
        }

        @DisplayName("평년 2월이면 1일부터 28일까지다.")
        @Test
        void commonFebruary() {
            // arrange
            LocalDate baseDate = LocalDate.of(2026, 2, 10);

            // act
            PeriodRange range = RankingPeriodType.MONTHLY.rangeOf(baseDate);

            // assert
            assertThat(range).isEqualTo(
                new PeriodRange(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28))
            );
        }

        @DisplayName("기준일이 말일이어도 해당 월 전체 범위를 반환한다.")
        @Test
        void baseDateIsLastDay() {
            // arrange
            LocalDate lastDay = LocalDate.of(2026, 7, 31);

            // act
            PeriodRange range = RankingPeriodType.MONTHLY.rangeOf(lastDay);

            // assert
            assertThat(range).isEqualTo(
                new PeriodRange(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31))
            );
        }
    }

    @DisplayName("RankingPeriodType.tableName() 은, ")
    @Nested
    class TableName {

        @DisplayName("각 기간 타입에 대응하는 MV 테이블명을 반환한다.")
        @Test
        void returnsMvTableName() {
            assertThat(RankingPeriodType.WEEKLY.tableName()).isEqualTo("mv_product_rank_weekly");
            assertThat(RankingPeriodType.MONTHLY.tableName()).isEqualTo("mv_product_rank_monthly");
        }
    }
}
