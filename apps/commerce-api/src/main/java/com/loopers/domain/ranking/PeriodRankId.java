package com.loopers.domain.ranking;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 주간·월간 MV 복합 PK = (period_start, product_id). commerce-batch 가 JDBC 로 적재하고 api 는 읽기만 하므로
 * 쓰기용 생성자는 두지 않는다 — Hibernate 가 필드 접근으로 하이드레이션한다.
 */
@Getter
@Embeddable
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PeriodRankId implements Serializable {

    @Column(name = "period_start", nullable = false, updatable = false)
    private LocalDate periodStart;

    @Column(name = "product_id", nullable = false, updatable = false)
    private Long productId;
}
