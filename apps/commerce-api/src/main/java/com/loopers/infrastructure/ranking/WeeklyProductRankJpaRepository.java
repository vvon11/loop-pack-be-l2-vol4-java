package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.PeriodRankId;
import com.loopers.domain.ranking.WeeklyProductRank;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface WeeklyProductRankJpaRepository extends JpaRepository<WeeklyProductRank, PeriodRankId> {

    List<WeeklyProductRank> findByIdPeriodStart(LocalDate periodStart, Pageable pageable);

    long countByIdPeriodStart(LocalDate periodStart);
}
