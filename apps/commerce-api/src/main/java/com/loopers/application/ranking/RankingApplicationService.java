package com.loopers.application.ranking;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.common.PageResult;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.ranking.DailyRankingRepository;
import com.loopers.domain.ranking.PeriodRange;
import com.loopers.domain.ranking.PeriodRankingRepository;
import com.loopers.domain.ranking.ProductRank;
import com.loopers.domain.ranking.RankedProduct;
import com.loopers.domain.ranking.RankingKeys;
import com.loopers.domain.ranking.RankingPeriod;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Component
public class RankingApplicationService {

    private final DailyRankingRepository dailyRankingRepository;
    private final PeriodRankingRepository periodRankingRepository;
    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;

    /**
     * 랭킹 페이지 조회 — DAILY 는 Redis, WEEKLY/MONTHLY 는 MySQL MV 에서 조회한다. 저장 기술이 다른 두 경로를
     * {@link RankEntry} 로 정규화해 상품·브랜드 일괄 조인 조립 로직 하나를 공유한다.
     *
     * <p>rank 는 각 저장소 어댑터가 채운 값을 그대로 쓴다 — 삭제 상품을 빼고 재번호를 매기면 같은 상품의
     * 순위가 페이지 조회와 단건 순위 조회에서 어긋난다. 따라서 삭제 상품 자리는 gap 으로 남고
     * 페이지가 size 미만일 수 있다.</p>
     */
    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    public RankingInfo.PeriodResult getRankings(RankingPeriod period, LocalDate baseDate, int page, int size) {
        PeriodRange range = period.rangeOf(baseDate);
        List<RankEntry> entries;
        long total;

        if (period == RankingPeriod.DAILY) {
            entries = dailyRankingRepository.page(baseDate, page, size).stream()
                    .map(r -> new RankEntry(r.rank(), r.productId(), r.score()))
                    .toList();
            total = dailyRankingRepository.total(baseDate);
        } else {
            entries = periodRankingRepository.page(period, range.start(), page, size).stream()
                    .map(r -> new RankEntry(r.getRank(), r.getProductId(), r.getScore().doubleValue()))
                    .toList();
            total = periodRankingRepository.total(period, range.start());
        }

        PageResult<RankingInfo.RankedItem> result = assemble(entries, page, size, total);
        return new RankingInfo.PeriodResult(period, range, result);
    }

    private PageResult<RankingInfo.RankedItem> assemble(List<RankEntry> ranked, int page, int size, long total) {
        if (ranked.isEmpty()) {
            return new PageResult<>(List.of(), page, size, false, total);
        }

        // IN 절은 순서를 보장하지 않는다 — Map 으로 받아 저장소가 매긴 rank 순서대로 재조립한다.
        Set<Long> productIds = ranked.stream().map(RankEntry::productId).collect(Collectors.toSet());
        Map<Long, Product> productById = productRepository.findAllByIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        Set<Long> brandIds = productById.values().stream().map(Product::getBrandId).collect(Collectors.toSet());
        Map<Long, Brand> brandById = brandRepository.findAllByIds(brandIds).stream()
                .collect(Collectors.toMap(Brand::getId, Function.identity()));

        List<RankingInfo.RankedItem> items = new ArrayList<>();
        for (RankEntry entry : ranked) {
            Product product = productById.get(entry.productId());
            if (product == null) {
                // soft-delete 된 상품 — findAllByIds 가 걸러낸다. 보드에는 남아 있으나 응답에서 제외(gap 허용).
                continue;
            }
            Brand brand = brandById.get(product.getBrandId());
            if (brand == null) {
                // 브랜드 소실 이상 데이터로 랭킹 조회 전체가 실패하지 않도록 해당 항목만 제외한다.
                log.warn("랭킹 항목 브랜드 소실로 제외: productId={}, brandId={}", product.getId(), product.getBrandId());
                continue;
            }
            items.add(RankingInfo.RankedItem.from(entry.rank(), entry.score(), product, brand));
        }

        boolean hasNext = (long) (page + 1) * size < total;
        return new PageResult<>(items, page, size, hasNext, total);
    }

    /**
     * 상품 상세 화면 부가 정보용 오늘 순위. 부가 정보의 실패가 본 응답(상품 상세)까지 실패시키면 안 되므로
     * 이 경로만 실패를 null 로 흡수한다 — 랭킹 API 자체는 실패를 그대로 노출하는 것과 대비되는 지점.
     */
    public Long getTodayRankOrNull(Long productId) {
        try {
            return dailyRankingRepository.rankOf(LocalDate.now(RankingKeys.ZONE), productId)
                    .map(ProductRank::rank)
                    .orElse(null);
        } catch (Exception e) {
            log.warn("상품 상세 순위 조회 실패 — rank 없이 응답 (productId={})", productId, e);
            return null;
        }
    }

    /** DAILY(Redis)/WEEKLY·MONTHLY(MV) 조회 결과를 조립 로직 하나로 합치기 위한 저장소 무관 중간 표현. */
    private record RankEntry(long rank, long productId, double score) {
    }
}
