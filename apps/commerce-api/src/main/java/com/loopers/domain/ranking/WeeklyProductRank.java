package com.loopers.domain.ranking;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "mv_product_rank_weekly",
        uniqueConstraints = @UniqueConstraint(name = "uk_weekly_period_rank", columnNames = {"period_start", "rank_no"})
)
public class WeeklyProductRank extends PeriodProductRank {
}
