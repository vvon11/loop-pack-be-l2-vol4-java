package com.loopers.application.product;

import com.loopers.domain.common.PageResult;
import com.loopers.domain.product.ProductCommand;

import java.util.Optional;

/**
 * 상품 조회 결과(합성 DTO) 캐시 포트 — cache-aside.
 * <p>
 * 캐시 대상은 엔티티가 아니라 합성 결과(브랜드명·재고까지 조립된 {@link ProductInfo.ListItem}/{@link ProductInfo.Detail})다.
 * <ul>
 *   <li><b>목록</b>: 첫 N페이지 + 허용 size 만 캐시(키 cardinality 상한). 정밀 무효화 없이 짧은 TTL 로만 신선도를 관리한다.
 *       — deep page/비표준 size 는 캐시하지 않고 항상 DB 로 간다(롱테일이라 캐시 이득이 작다).</li>
 *   <li><b>상세</b>: PK 단건 키라 무효화가 싸다. 짧은 TTL + 상품 쓰기(modify/delete) 시 evict 로 신선도를 관리한다.
 *       — likeCount/재고 변동(좋아요/주문)은 evict 하지 않고 TTL 로만 흡수한다.</li>
 * </ul>
 * 모든 연산은 장애 시 캐시를 우회한다(가용성 우선). 구현(어댑터)은 infrastructure 에 둔다.
 */
public interface ProductCacheRepository {

    Optional<PageResult<ProductInfo.ListItem>> findList(ProductCommand.Search search);

    void putList(ProductCommand.Search search, PageResult<ProductInfo.ListItem> page);

    Optional<ProductInfo.Detail> findDetail(Long productId);

    void putDetail(ProductInfo.Detail detail);

    void evictDetail(Long productId);
}
