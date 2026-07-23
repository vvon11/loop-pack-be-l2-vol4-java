package com.loopers.batch.job.ranking.step;

import com.loopers.batch.domain.ranking.PeriodRange;
import com.loopers.batch.domain.ranking.PeriodRankRow;
import com.loopers.batch.domain.ranking.RankingPeriodType;
import com.loopers.batch.infrastructure.ranking.PeriodRankMvJdbcRepository;
import org.springframework.batch.core.ChunkListener;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;

import java.util.List;

/**
 * 대상 기간 MV 삭제 + TOP N 적재를 한 chunk(=한 트랜잭션)에서 수행한다. {@code beforeChunk} 에서 먼저 삭제해
 * 집계 결과가 0건이라 {@link #write} 가 호출되지 않는 경우에도 삭제가 보장되게 하고, write() 에서는 방어적으로
 * (멱등) 한 번 더 호출한다. 삭제·적재가 같은 트랜잭션이라 적재 실패 시 삭제까지 롤백되어 이전 MV 가 보존된다.
 *
 * <p>{@code Step} 이 단일 스레드로 실행되므로 {@code deleted} 플래그에 동기화가 필요 없다.</p>
 */
public class PeriodRankMvItemWriter implements ItemWriter<PeriodRankRow>, ChunkListener {

    private final PeriodRankMvJdbcRepository repository;
    private final RankingPeriodType periodType;
    private final PeriodRange range;
    private boolean deleted = false;

    public PeriodRankMvItemWriter(PeriodRankMvJdbcRepository repository, RankingPeriodType periodType, PeriodRange range) {
        this.repository = repository;
        this.periodType = periodType;
        this.range = range;
    }

    @Override
    public void beforeChunk(ChunkContext context) {
        deleteOnce();
    }

    @Override
    public void write(Chunk<? extends PeriodRankRow> chunk) {
        deleteOnce();
        repository.insertAll(periodType, List.copyOf(chunk.getItems()));
    }

    private void deleteOnce() {
        if (deleted) {
            return;
        }
        repository.deleteByPeriodStart(periodType, range.start());
        deleted = true;
    }
}
