package com.loopers.domain.ranking;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RankingPeriodTest {

    @DisplayName("RankingPeriod.from() 은, ")
    @Nested
    class From {

        @DisplayName("null 이면 DAILY 를 반환한다.")
        @Test
        void returnsDaily_whenRawIsNull() {
            assertThat(RankingPeriod.from(null)).isEqualTo(RankingPeriod.DAILY);
        }

        @DisplayName("공백이면 DAILY 를 반환한다.")
        @Test
        void returnsDaily_whenRawIsBlank() {
            assertThat(RankingPeriod.from("  ")).isEqualTo(RankingPeriod.DAILY);
        }

        @DisplayName("대소문자를 구분하지 않는다.")
        @Test
        void isCaseInsensitive() {
            assertThat(RankingPeriod.from("weekly")).isEqualTo(RankingPeriod.WEEKLY);
            assertThat(RankingPeriod.from("Monthly")).isEqualTo(RankingPeriod.MONTHLY);
        }

        @DisplayName("지원하지 않는 값이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenRawIsUnsupported() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> RankingPeriod.from("YEARLY"));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("RankingPeriod.rangeOf() 은, ")
    @Nested
    class RangeOf {

        @DisplayName("DAILY 는 기준일 하루만 범위로 반환한다.")
        @Test
        void daily_returnsSameDayRange() {
            // arrange
            LocalDate baseDate = LocalDate.of(2026, 7, 24);

            // act
            PeriodRange range = RankingPeriod.DAILY.rangeOf(baseDate);

            // assert
            assertThat(range).isEqualTo(new PeriodRange(baseDate, baseDate));
        }

        @DisplayName("WEEKLY 는 기준일이 월요일이면 자기 자신부터 그 주 일요일까지다.")
        @Test
        void weekly_baseDateIsMonday() {
            // arrange
            LocalDate monday = LocalDate.of(2026, 7, 20);

            // act
            PeriodRange range = RankingPeriod.WEEKLY.rangeOf(monday);

            // assert
            assertThat(range).isEqualTo(new PeriodRange(monday, LocalDate.of(2026, 7, 26)));
        }

        @DisplayName("WEEKLY 는 기준일이 일요일이면 그 주 월요일부터 자기 자신까지다.")
        @Test
        void weekly_baseDateIsSunday() {
            // arrange
            LocalDate sunday = LocalDate.of(2026, 7, 26);

            // act
            PeriodRange range = RankingPeriod.WEEKLY.rangeOf(sunday);

            // assert
            assertThat(range).isEqualTo(new PeriodRange(LocalDate.of(2026, 7, 20), sunday));
        }

        @DisplayName("WEEKLY 는 연도가 바뀌는 주도 올바르게 계산한다.")
        @Test
        void weekly_crossesYearBoundary() {
            // arrange
            LocalDate baseDate = LocalDate.of(2026, 1, 1);

            // act
            PeriodRange range = RankingPeriod.WEEKLY.rangeOf(baseDate);

            // assert
            assertThat(range).isEqualTo(
                new PeriodRange(LocalDate.of(2025, 12, 29), LocalDate.of(2026, 1, 4))
            );
        }

        @DisplayName("MONTHLY 는 윤년 2월이면 1일부터 29일까지다.")
        @Test
        void monthly_leapFebruary() {
            // arrange
            LocalDate baseDate = LocalDate.of(2024, 2, 15);

            // act
            PeriodRange range = RankingPeriod.MONTHLY.rangeOf(baseDate);

            // assert
            assertThat(range).isEqualTo(
                new PeriodRange(LocalDate.of(2024, 2, 1), LocalDate.of(2024, 2, 29))
            );
        }

        @DisplayName("MONTHLY 는 평년 2월이면 1일부터 28일까지다.")
        @Test
        void monthly_commonFebruary() {
            // arrange
            LocalDate baseDate = LocalDate.of(2026, 2, 10);

            // act
            PeriodRange range = RankingPeriod.MONTHLY.rangeOf(baseDate);

            // assert
            assertThat(range).isEqualTo(
                new PeriodRange(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28))
            );
        }

        @DisplayName("MONTHLY 는 기준일이 말일이어도 해당 월 전체 범위를 반환한다.")
        @Test
        void monthly_baseDateIsLastDay() {
            // arrange
            LocalDate lastDay = LocalDate.of(2026, 7, 31);

            // act
            PeriodRange range = RankingPeriod.MONTHLY.rangeOf(lastDay);

            // assert
            assertThat(range).isEqualTo(
                new PeriodRange(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31))
            );
        }
    }
}
