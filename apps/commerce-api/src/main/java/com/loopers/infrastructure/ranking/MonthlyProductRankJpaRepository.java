package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.MonthlyProductRank;
import com.loopers.domain.ranking.PeriodRankId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface MonthlyProductRankJpaRepository extends JpaRepository<MonthlyProductRank, PeriodRankId> {

    List<MonthlyProductRank> findByIdPeriodStart(LocalDate periodStart, Pageable pageable);

    long countByIdPeriodStart(LocalDate periodStart);
}
