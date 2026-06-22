package com.loopers.application.product;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.common.PageResult;
import com.loopers.domain.inventory.Inventory;
import com.loopers.domain.product.Money;
import com.loopers.domain.product.Product;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.inventory.InventoryJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class ProductCacheIntegrationTest {

    @Autowired
    private ProductApplicationService productApplicationService;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private InventoryJpaRepository inventoryJpaRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private RedisCleanUp redisCleanUp;

    private Long brandId;

    @BeforeEach
    void setUp() {
        brandId = brandJpaRepository.save(Brand.create("브랜드A", "소개A")).getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    private Product saveProduct(String name, long price) {
        Product product = productJpaRepository.save(Product.create(brandId, name, Money.of(price)));
        inventoryJpaRepository.save(Inventory.create(product.getId(), 10));
        return product;
    }

    private PageResult<ProductInfo.ListItem> getPage(int page, int size) {
        return productApplicationService.getAllProducts(
                new ProductCriteria.GetAll(page, size, null, "LATEST"));
    }

    @Nested
    @DisplayName("목록 캐시 — 첫 N페이지 + 허용 size 만 캐시")
    class ListCache {

        @Test
        @DisplayName("page 0, size 20(허용)은 캐시된다 — 적재 후 새 상품이 등록돼도 TTL 내에는 옛 결과가 반환된다.")
        void cachesWhitelistedFirstPage() {
            Product p1 = saveProduct("상품1", 1_000L);
            Product p2 = saveProduct("상품2", 2_000L);

            // 1차: page 0 적재 (LATEST DESC → [p2, p1])
            PageResult<ProductInfo.ListItem> first = getPage(0, 20);
            assertThat(first.content()).extracting(ProductInfo.ListItem::id)
                    .containsExactly(p2.getId(), p1.getId());

            // 새 상품 등록 — 목록은 evict 하지 않으므로 캐시는 갱신되지 않는다.
            saveProduct("상품3", 3_000L);

            // 2차: 캐시 히트 → 여전히 [p2, p1]. (캐시를 안 탔다면 [p3, p2, p1] 였을 것)
            PageResult<ProductInfo.ListItem> second = getPage(0, 20);
            assertThat(second.content()).extracting(ProductInfo.ListItem::id)
                    .containsExactly(p2.getId(), p1.getId());
        }

        @Test
        @DisplayName("비표준 size(허용 목록 밖)는 캐시하지 않는다 — 항상 최신 DB 결과가 반환된다.")
        void skipsNonWhitelistedSize() {
            Product p1 = saveProduct("상품1", 1_000L);
            Product p2 = saveProduct("상품2", 2_000L);

            // size 5 는 화이트리스트({20}) 밖 → 캐시 안 함
            PageResult<ProductInfo.ListItem> first = getPage(0, 5);
            assertThat(first.content()).extracting(ProductInfo.ListItem::id)
                    .containsExactly(p2.getId(), p1.getId());
            assertThat(redisTemplate.keys("product:list:*")).isEmpty();

            Product p3 = saveProduct("상품3", 3_000L);

            // 캐시 미스(항상 DB) → 새 상품이 즉시 반영된다.
            PageResult<ProductInfo.ListItem> second = getPage(0, 5);
            assertThat(second.content()).extracting(ProductInfo.ListItem::id)
                    .containsExactly(p3.getId(), p2.getId(), p1.getId());
        }

        @Test
        @DisplayName("깊은 페이지(page > 1)는 캐시하지 않는다 — 캐시 키가 생성되지 않는다.")
        void skipsDeepPage() {
            saveProduct("상품1", 1_000L);
            saveProduct("상품2", 2_000L);

            getPage(2, 20);

            // page 2 키는 적재되지 않는다(롱테일이라 캐시 제외).
            assertThat(redisTemplate.keys("product:list:all:LATEST:2:20")).isEmpty();
        }
    }

    @Nested
    @DisplayName("상세 캐시 — PK 단건, 쓰기 시 evict")
    class DetailCache {

        @Test
        @DisplayName("상세는 캐시된다 — 적재 후 DB 가 바뀌어도(evict 우회) TTL 내에는 옛 결과가 반환된다.")
        void cachesDetail() {
            Product p = saveProduct("상품1", 1_000L);

            // 1차: 캐시 적재
            ProductInfo.Detail first = productApplicationService.getProduct(p.getId());
            assertThat(first.name()).isEqualTo("상품1");

            // 캐시 evict 를 우회해 DB 만 직접 변경한다.
            Product loaded = productJpaRepository.findById(p.getId()).orElseThrow();
            loaded.modify("변경된이름", Money.of(2_000L));
            productJpaRepository.saveAndFlush(loaded);

            // 2차: 캐시 히트 → 여전히 옛 이름.
            ProductInfo.Detail second = productApplicationService.getProduct(p.getId());
            assertThat(second.name()).isEqualTo("상품1");
        }

        @Test
        @DisplayName("modify 시 상세 캐시가 evict 된다 — 다음 조회는 변경된 값을 반영한다.")
        void evictsDetailOnModify() {
            Product p = saveProduct("상품1", 1_000L);
            productApplicationService.getProduct(p.getId()); // 캐시 적재

            productApplicationService.modify(new ProductCriteria.Modify(p.getId(), "변경된이름", 2_000L, 10));

            ProductInfo.Detail after = productApplicationService.getProduct(p.getId());
            assertThat(after.name()).isEqualTo("변경된이름");
            assertThat(after.price()).isEqualTo(2_000L);
        }

        @Test
        @DisplayName("delete 시 상세 캐시가 evict 된다 — 삭제된 상품은 캐시로 노출되지 않고 NOT_FOUND 가 된다.")
        void evictsDetailOnDelete() {
            Product p = saveProduct("상품1", 1_000L);
            productApplicationService.getProduct(p.getId()); // 캐시 적재

            productApplicationService.delete(p.getId());

            // evict 되지 않았다면 캐시된 상세가 그대로 반환됐을 것 — evict 됐으므로 DB 의 soft-deleted 가 드러난다.
            assertThatThrownBy(() -> productApplicationService.getProduct(p.getId()))
                    .isInstanceOf(CoreException.class);
        }
    }
}