package com.loopers.domain.metrics;

import com.loopers.domain.ranking.RankingKeys;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.ZonedDateTime;

/**
 * product_metrics 복합 PK = (metric_date, product_id). 상품별 누적 총계였던 이전 구조를 일자별로 쪼개
 * 주간·월간 배치가 {@code metric_date BETWEEN} 범위 SUM 을 할 수 있게 한다. {@code @EmbeddedId} 이므로
 * equals/hashCode 가 필수 — event_handled 의 {@code EventHandledId} 와 같은 패턴.
 */
@Getter
@Embeddable
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductMetricsId implements Serializable {

    @Column(name = "metric_date", nullable = false, updatable = false)
    private LocalDate metricDate;

    @Column(name = "product_id", nullable = false, updatable = false)
    private Long productId;

    public ProductMetricsId(LocalDate metricDate, Long productId) {
        if (productId == null) {
            throw new IllegalArgumentException("productId must not be null");
        }
        this.metricDate = metricDate;
        this.productId = productId;
    }

    /** 이벤트 발생 시각을 KST 달력일로 귀속한다(자정 경계 지연 도착 대비, RankingKeys.bucketOf 와 동일 규칙). */
    public static ProductMetricsId of(ZonedDateTime occurredAt, Long productId) {
        return new ProductMetricsId(RankingKeys.bucketOf(occurredAt), productId);
    }
}
