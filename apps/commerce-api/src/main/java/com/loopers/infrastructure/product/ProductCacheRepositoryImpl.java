package com.loopers.infrastructure.product;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.product.ProductCacheRepository;
import com.loopers.application.product.ProductInfo;
import com.loopers.domain.common.PageResult;
import com.loopers.domain.product.ProductCommand;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

/**
 * cache-aside 어댑터. application 포트({@link ProductCacheRepository})를 구현한다(infrastructure → application).
 * <p>
 * 값은 ObjectMapper 로 JSON 문자열 직렬화 후 기존 {@code defaultRedisTemplate}(String/String, REPLICA_PREFERRED)에 저장한다.
 * 모든 Redis 연산은 try/catch 로 감싸 장애 시 캐시를 우회한다(가용성 우선) — GET 실패는 미스로 간주, SET/DEL 실패는 무시.
 * <p>
 * 목록은 첫 {@code MAX_CACHEABLE_PAGE} 페이지 + 허용 size({@code CACHEABLE_SIZES}) 만 캐시한다 — 키 cardinality 상한.
 * 메모리는 TTL + Redis maxmemory eviction 으로 관리한다. 상세는 PK 단건 키라 쓰기 시 정확히 evict 한다.
 */
@Slf4j
@Component
public class ProductCacheRepositoryImpl implements ProductCacheRepository {

    private static final String LIST_KEY_PREFIX = "product:list:";
    private static final String DETAIL_KEY_PREFIX = "product:detail:";
    private static final Duration LIST_TTL = Duration.ofSeconds(30);
    private static final Duration DETAIL_TTL = Duration.ofSeconds(30);

    // 캐시 대상 목록 키 상한 — page 0,1(=첫 2페이지) + 기본 size 20 만. 롱테일(깊은 페이지/비표준 size)은 캐시 제외.
    private static final int MAX_CACHEABLE_PAGE = 1;
    private static final Set<Integer> CACHEABLE_SIZES = Set.of(20);

    private static final TypeReference<PageResult<ProductInfo.ListItem>> LIST_TYPE = new TypeReference<>() {
    };

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public ProductCacheRepositoryImpl(RedisTemplate<String, String> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<PageResult<ProductInfo.ListItem>> findList(ProductCommand.Search search) {
        if (!isCacheable(search)) {
            return Optional.empty(); // 캐시 대상 아님 → Redis 우회, 호출자가 DB 재계산
        }
        String json = safeGet(listKey(search));
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, LIST_TYPE));
        } catch (Exception e) {
            log.warn("[cache] 목록 역직렬화 실패 - 미스 처리: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void putList(ProductCommand.Search search, PageResult<ProductInfo.ListItem> page) {
        if (!isCacheable(search)) {
            return; // 캐시 대상 아님 → 적재하지 않음(키 cardinality 상한)
        }
        try {
            redisTemplate.opsForValue().set(listKey(search), objectMapper.writeValueAsString(page), LIST_TTL);
        } catch (Exception e) {
            log.warn("[cache] 목록 SET 실패 - 캐시 우회 key={}: {}", listKey(search), e.getMessage());
        }
    }

    @Override
    public Optional<ProductInfo.Detail> findDetail(Long productId) {
        String json = safeGet(detailKey(productId));
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, ProductInfo.Detail.class));
        } catch (Exception e) {
            log.warn("[cache] 상세 역직렬화 실패 - 미스 처리 id={}: {}", productId, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void putDetail(ProductInfo.Detail detail) {
        try {
            redisTemplate.opsForValue().set(detailKey(detail.id()), objectMapper.writeValueAsString(detail), DETAIL_TTL);
        } catch (Exception e) {
            log.warn("[cache] 상세 SET 실패 - 캐시 우회 id={}: {}", detail.id(), e.getMessage());
        }
    }

    @Override
    public void evictDetail(Long productId) {
        try {
            redisTemplate.delete(detailKey(productId));
        } catch (RuntimeException e) {
            log.warn("[cache] 상세 DEL 실패 - 무시 id={}: {}", productId, e.getMessage());
        }
    }

    private boolean isCacheable(ProductCommand.Search s) {
        return s.page() <= MAX_CACHEABLE_PAGE && CACHEABLE_SIZES.contains(s.size());
    }

    private String safeGet(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (RuntimeException e) {
            log.warn("[cache] GET 실패 - 캐시 우회 key={}: {}", key, e.getMessage());
            return null;
        }
    }

    private String listKey(ProductCommand.Search s) {
        String brand = s.brandId() == null ? "all" : s.brandId().toString();
        return LIST_KEY_PREFIX + brand + ":" + s.sort() + ":" + s.page() + ":" + s.size();
    }

    private String detailKey(Long productId) {
        return DETAIL_KEY_PREFIX + productId;
    }
}