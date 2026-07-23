package com.loopers.batch.job.ranking.step;

import com.loopers.batch.domain.ranking.PeriodRange;
import com.loopers.batch.domain.ranking.ProductRankAggregate;
import com.loopers.batch.infrastructure.ranking.ProductMetricsAggregationJdbcRepository;
import org.springframework.batch.item.ItemReader;

import java.util.Iterator;
import java.util.List;

/**
 * 집계 SQL 을 1회 호출해 이터레이터로 소진하는 Reader. SQL 이 이미 {@code LIMIT} 으로 TOP N 만 반환하므로
 * 페이징/커서가 사줄 게 없다. {@code ItemStream} 을 의도적으로 구현하지 않아 chunk 이어처리가 타입 수준에서
 * 불가능하다 — 재시작은 항상 전체 재실행이다.
 */
public class ProductRankAggregationReader implements ItemReader<ProductRankAggregate> {

    private final Iterator<ProductRankAggregate> delegate;

    public ProductRankAggregationReader(
            ProductMetricsAggregationJdbcRepository repository, PeriodRange range, int limit) {
        this.delegate = repository.aggregate(range, limit).iterator();
    }

    @Override
    public ProductRankAggregate read() {
        return delegate.hasNext() ? delegate.next() : null;
    }
}
