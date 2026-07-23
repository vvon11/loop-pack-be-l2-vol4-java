package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.PeriodProductRank;
import com.loopers.domain.ranking.PeriodRankingRepository;
import com.loopers.domain.ranking.RankingPeriod;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/** {@link RankingPeriod} 값에 따라 weekly/monthly JPA 리포지토리로 위임하는 얇은 오케스트레이션 어댑터. */
@Repository
@RequiredArgsConstructor
public class PeriodRankingJpaRepository implements PeriodRankingRepository {

    private final WeeklyProductRankJpaRepository weeklyProductRankJpaRepository;
    private final MonthlyProductRankJpaRepository monthlyProductRankJpaRepository;

    @Override
    public List<PeriodProductRank> page(RankingPeriod period, LocalDate periodStart, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("rankNo").ascending());
        return switch (period) {
            case WEEKLY -> List.copyOf(weeklyProductRankJpaRepository.findByIdPeriodStart(periodStart, pageable));
            case MONTHLY -> List.copyOf(monthlyProductRankJpaRepository.findByIdPeriodStart(periodStart, pageable));
            case DAILY -> throw unsupportedDaily();
        };
    }

    @Override
    public long total(RankingPeriod period, LocalDate periodStart) {
        return switch (period) {
            case WEEKLY -> weeklyProductRankJpaRepository.countByIdPeriodStart(periodStart);
            case MONTHLY -> monthlyProductRankJpaRepository.countByIdPeriodStart(periodStart);
            case DAILY -> throw unsupportedDaily();
        };
    }

    private static IllegalArgumentException unsupportedDaily() {
        return new IllegalArgumentException("DAILY 는 DailyRankingRepository(Redis) 를 사용해야 합니다.");
    }
}
