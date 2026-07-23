package com.loopers.domain.metrics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductMetricsIdTest {

    @DisplayName("ProductMetricsId.of() 는, ")
    @Nested
    class Of {

        @DisplayName("occurredAt 을 KST 달력일로 귀속해 productId 와 조합한다 — UTC 15:00 은 KST 로 다음날이다.")
        @Test
        void bucketsOccurredAtToKstDate() {
            // arrange
            ZonedDateTime occurredAt = ZonedDateTime.of(2026, 7, 15, 15, 0, 0, 0, ZoneOffset.UTC);

            // act
            ProductMetricsId id = ProductMetricsId.of(occurredAt, 1L);

            // assert
            assertThat(id.getMetricDate()).isEqualTo(LocalDate.of(2026, 7, 16));
            assertThat(id.getProductId()).isEqualTo(1L);
        }

        @DisplayName("productId 가 null 이면 IllegalArgumentException 이 발생한다(poison 메시지 DLQ 격리 경로).")
        @Test
        void throws_whenProductIdIsNull() {
            assertThrows(IllegalArgumentException.class,
                () -> ProductMetricsId.of(ZonedDateTime.now(), null));
        }
    }

    @DisplayName("ProductMetricsId 는, ")
    @Nested
    class EqualsAndHashCode {

        @DisplayName("metricDate 와 productId 가 같으면 동등하다.")
        @Test
        void equalWhenSameFields() {
            // arrange
            LocalDate date = LocalDate.of(2026, 7, 20);

            // act
            ProductMetricsId a = new ProductMetricsId(date, 1L);
            ProductMetricsId b = new ProductMetricsId(date, 1L);

            // assert
            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @DisplayName("productId 가 다르면 동등하지 않다.")
        @Test
        void notEqualWhenProductIdDiffers() {
            // arrange
            LocalDate date = LocalDate.of(2026, 7, 20);

            // act
            ProductMetricsId a = new ProductMetricsId(date, 1L);
            ProductMetricsId b = new ProductMetricsId(date, 2L);

            // assert
            assertThat(a).isNotEqualTo(b);
        }

        @DisplayName("metricDate 가 다르면 동등하지 않다.")
        @Test
        void notEqualWhenMetricDateDiffers() {
            // act
            ProductMetricsId a = new ProductMetricsId(LocalDate.of(2026, 7, 20), 1L);
            ProductMetricsId b = new ProductMetricsId(LocalDate.of(2026, 7, 21), 1L);

            // assert
            assertThat(a).isNotEqualTo(b);
        }
    }
}
