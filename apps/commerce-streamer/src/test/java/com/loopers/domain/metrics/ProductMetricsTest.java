package com.loopers.domain.metrics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ProductMetrics 는 이벤트 투영 read model 이라 Spring 없이 순수 POJO 로 카운터 증감 규칙만 검증한다.
 * (동시성·@DynamicUpdate 의 컬럼단위 쓰기는 영속성 관심사라 통합 테스트에서 확인한다.)
 */
class ProductMetricsTest {

    private static final ProductMetricsId ID = new ProductMetricsId(LocalDate.of(2026, 7, 20), 1L);

    @Nested
    @DisplayName("좋아요 카운터")
    class Like {
        @Test
        @DisplayName("increaseLike 는 1 증가시킨다.")
        void increaseLike() {
            ProductMetrics metrics = ProductMetrics.of(ID);
            metrics.increaseLike();
            assertThat(metrics.getLikeCount()).isEqualTo(1L);
        }

        @Test
        @DisplayName("decreaseLike 는 일별 순증감이므로 0 미만(음수)도 허용한다.")
        void decreaseLikeAllowsNegative() {
            ProductMetrics metrics = ProductMetrics.of(ID);
            metrics.decreaseLike();
            assertThat(metrics.getLikeCount()).isEqualTo(-1L);
        }
    }

    @Nested
    @DisplayName("판매량 카운터")
    class Sales {
        @Test
        @DisplayName("increaseSales 는 주문 수량만큼 누적한다.")
        void increaseSalesByQuantity() {
            ProductMetrics metrics = ProductMetrics.of(ID);
            metrics.increaseSales(3);
            metrics.increaseSales(2);
            assertThat(metrics.getSalesCount()).isEqualTo(5L);
        }
    }

    @Nested
    @DisplayName("조회수 카운터")
    class View {
        @Test
        @DisplayName("increaseView 는 1 증가시킨다.")
        void increaseView() {
            ProductMetrics metrics = ProductMetrics.of(ID);
            metrics.increaseView();
            metrics.increaseView();
            assertThat(metrics.getViewCount()).isEqualTo(2L);
        }
    }

    @Test
    @DisplayName("한 카운터의 증감은 다른 카운터에 영향을 주지 않는다(컬럼 독립).")
    void countersAreIndependent() {
        ProductMetrics metrics = ProductMetrics.of(ID);
        metrics.increaseLike();
        metrics.increaseSales(4);
        metrics.increaseView();
        assertThat(metrics.getLikeCount()).isEqualTo(1L);
        assertThat(metrics.getSalesCount()).isEqualTo(4L);
        assertThat(metrics.getViewCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("productId/metricDate 위임 게터는 id 의 값을 그대로 반환한다.")
    void delegateGettersReflectId() {
        ProductMetrics metrics = ProductMetrics.of(ID);
        assertThat(metrics.getProductId()).isEqualTo(1L);
        assertThat(metrics.getMetricDate()).isEqualTo(LocalDate.of(2026, 7, 20));
    }
}
