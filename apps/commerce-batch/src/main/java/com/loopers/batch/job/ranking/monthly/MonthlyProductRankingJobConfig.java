package com.loopers.batch.job.ranking.monthly;

import com.loopers.batch.domain.ranking.PeriodRange;
import com.loopers.batch.domain.ranking.PeriodRankRow;
import com.loopers.batch.domain.ranking.ProductRankAggregate;
import com.loopers.batch.domain.ranking.RankingPeriodType;
import com.loopers.batch.domain.ranking.RankingPolicy;
import com.loopers.batch.domain.ranking.TargetDate;
import com.loopers.batch.infrastructure.ranking.PeriodRankMvJdbcRepository;
import com.loopers.batch.infrastructure.ranking.ProductMetricsAggregationJdbcRepository;
import com.loopers.batch.job.ranking.step.PeriodRankItemProcessor;
import com.loopers.batch.job.ranking.step.PeriodRankMvItemWriter;
import com.loopers.batch.job.ranking.step.ProductRankAggregationReader;
import com.loopers.batch.listener.JobListener;
import com.loopers.batch.listener.StepMonitorListener;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 월간 TOP 100 랭킹 MV({@code mv_product_rank_monthly}) 적재 Job. {@code WeeklyProductRankingJobConfig}
 * 와 상수(JOB_NAME/STEP_NAME/PERIOD_TYPE)만 다른 사본이다 — 추상 베이스로 묶지 않는다. {@code @Bean} 메서드는
 * 이름이 달라야 해 결국 각 클래스에 다시 선언해야 하고, 실제 공유 대상(Reader/Processor/Writer 구현·리포지토리·
 * 기간계산)은 이미 클래스 단위로 공유되어 있다.
 */
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = MonthlyProductRankingJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Configuration
public class MonthlyProductRankingJobConfig {

    public static final String JOB_NAME = "monthlyProductRankingJob";
    private static final String STEP_NAME = "monthlyProductRankingStep";
    private static final RankingPeriodType PERIOD_TYPE = RankingPeriodType.MONTHLY;

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final JobListener jobListener;
    private final StepMonitorListener stepMonitorListener;
    private final ProductMetricsAggregationJdbcRepository aggregationRepository;
    private final PeriodRankMvJdbcRepository periodRankMvJdbcRepository;

    @Bean(JOB_NAME)
    public Job monthlyProductRankingJob() {
        return new JobBuilder(JOB_NAME, jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(monthlyProductRankingStep())
                .listener(jobListener)
                .build();
    }

    @JobScope
    @Bean(STEP_NAME)
    public Step monthlyProductRankingStep() {
        PeriodRankMvItemWriter writer = monthlyRankWriter(null);
        return new StepBuilder(STEP_NAME, jobRepository)
                .<ProductRankAggregate, PeriodRankRow>chunk(RankingPolicy.TOP_N, transactionManager)
                .reader(monthlyRankReader(null))
                .processor(monthlyRankProcessor(null))
                .writer(writer)
                .listener(writer) // ChunkListener — 빈 청크에도 beforeChunk 삭제를 보장
                .listener(stepMonitorListener)
                .build();
    }

    // PeriodRange 는 record(= final) 라 @StepScope 의 CGLIB 스코프 프록시 대상이 될 수 없다.
    // 그래서 별도 @Bean 으로 캐지 않고 Reader/Processor/Writer 각자가 targetDate 로부터 계산한다.
    private static PeriodRange rangeOf(String targetDate) {
        return PERIOD_TYPE.rangeOf(TargetDate.parse(targetDate).value());
    }

    @StepScope
    @Bean
    public ProductRankAggregationReader monthlyRankReader(@Value("#{jobParameters['targetDate']}") String targetDate) {
        return new ProductRankAggregationReader(aggregationRepository, rangeOf(targetDate), RankingPolicy.TOP_N);
    }

    @StepScope
    @Bean
    public PeriodRankItemProcessor monthlyRankProcessor(@Value("#{jobParameters['targetDate']}") String targetDate) {
        return new PeriodRankItemProcessor(rangeOf(targetDate));
    }

    @StepScope
    @Bean
    public PeriodRankMvItemWriter monthlyRankWriter(@Value("#{jobParameters['targetDate']}") String targetDate) {
        return new PeriodRankMvItemWriter(periodRankMvJdbcRepository, PERIOD_TYPE, rangeOf(targetDate));
    }
}
