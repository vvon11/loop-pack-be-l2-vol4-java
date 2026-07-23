package com.loopers.domain.ranking;

import java.time.LocalDate;
import java.util.List;

/**
 * 주간·월간 MV 조회 포트(MySQL). {@link RankingPeriod#DAILY} 는 {@link DailyRankingRepository}(Redis) 를
 * 쓰므로 이 포트에 도달하면 안 된다 — 어댑터가 방어적으로 예외를 던진다.
 */
public interface PeriodRankingRepository {

    /** rank_no 오름차순 페이지 조회. 대상 기간 MV 가 없으면 빈 목록. */
    List<PeriodProductRank> page(RankingPeriod period, LocalDate periodStart, int page, int size);

    /** 대상 기간 MV 전체 행 수(최대 TOP_N). MV 가 없으면 0. */
    long total(RankingPeriod period, LocalDate periodStart);
}
