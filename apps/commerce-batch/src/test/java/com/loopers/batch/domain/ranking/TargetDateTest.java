package com.loopers.batch.domain.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TargetDateTest {

    @DisplayName("TargetDate.parse() 는, ")
    @Nested
    class Parse {

        @DisplayName("yyyyMMdd 형식 문자열을 LocalDate 로 변환한다.")
        @Test
        void parsesBasicIsoDate() {
            // act
            TargetDate result = TargetDate.parse("20260720");

            // assert
            assertThat(result.value()).isEqualTo(LocalDate.of(2026, 7, 20));
        }

        @DisplayName("null 이면 예외가 발생한다.")
        @Test
        void throws_whenRawIsNull() {
            assertThrows(IllegalArgumentException.class, () -> TargetDate.parse(null));
        }

        @DisplayName("공백이면 예외가 발생한다.")
        @Test
        void throws_whenRawIsBlank() {
            assertThrows(IllegalArgumentException.class, () -> TargetDate.parse("   "));
        }

        @DisplayName("yyyyMMdd 형식이 아니면 예외가 발생한다.")
        @Test
        void throws_whenRawIsMalformed() {
            assertThrows(IllegalArgumentException.class, () -> TargetDate.parse("2026-07-20"));
        }
    }
}
