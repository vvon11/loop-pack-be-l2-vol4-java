package com.loopers.domain.ranking;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.MappedSuperclass;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;

/**
 * 주간·월간 MV 공통 매핑. commerce-batch 가 JDBC 로 적재한 조회 전용 투영이라 api 는 쓰기 API 없이 읽기만
 * 제공한다. 복합키({@link PeriodRankId})라 {@code BaseEntity} 를 상속하지 않는다({@code ProductMetrics} 와
 * 같은 이유). 컬럼명은 {@code rank_no}(MySQL 예약어 회피) 지만 도메인 게터는 {@code rank} 로 노출한다.
 */
@Getter
@MappedSuperclass
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class PeriodProductRank {

    @EmbeddedId
    private PeriodRankId id;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "rank_no", nullable = false)
    private long rankNo;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    @Column(name = "like_count", nullable = false)
    private long likeCount;

    @Column(name = "sales_count", nullable = false)
    private long salesCount;

    @Column(name = "score", nullable = false, precision = 19, scale = 4)
    private BigDecimal score;

    @Column(name = "calculated_at", nullable = false)
    private ZonedDateTime calculatedAt;

    public long getRank() {
        return rankNo;
    }

    public Long getProductId() {
        return id.getProductId();
    }

    public LocalDate getPeriodStart() {
        return id.getPeriodStart();
    }
}
