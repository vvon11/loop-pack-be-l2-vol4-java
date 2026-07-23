package com.loopers.batch.job.ranking.step;

import com.loopers.batch.domain.ranking.PeriodRange;
import com.loopers.batch.domain.ranking.PeriodRankRow;
import com.loopers.batch.domain.ranking.ProductRankAggregate;
import com.loopers.batch.domain.ranking.RankingPolicy;
import org.springframework.batch.item.ItemProcessor;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/**
 * 집계 SQL 이 이미 score/rank_no 를 계산했지만, Processor 에만 있을 수 있는 책임을 담당한다: ① calculatedAt
 * 을 이번 실행 단위로 1개 고정(row 마다 now() 면 100행이 제각각), ② 기간 메타(periodStart/End) 부착,
 * ③ rankNo 가 TOP_N 을 넘지 않는다는 불변식 방어.
 */
public class PeriodRankItemProcessor implements ItemProcessor<ProductRankAggregate, PeriodRankRow> {

    private final PeriodRange range;
    private final ZonedDateTime calculatedAt = ZonedDateTime.now(ZoneOffset.UTC);

    public PeriodRankItemProcessor(PeriodRange range) {
        this.range = range;
    }

    @Override
    public PeriodRankRow process(ProductRankAggregate item) {
        if (item.rankNo() > RankingPolicy.TOP_N) {
            throw new IllegalStateException("집계 SQL 이 TOP_N(" + RankingPolicy.TOP_N + ") 을 초과하는 rank_no 를 반환했습니다: " + item.rankNo());
        }

        return new PeriodRankRow(
                range.start(), range.end(),
                item.productId(), item.rankNo(),
                item.viewCount(), item.likeCount(), item.salesCount(), item.score(),
                calculatedAt
        );
    }
}
