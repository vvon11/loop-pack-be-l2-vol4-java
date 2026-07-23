package com.loopers.domain.ranking;

/**
 * display 보드의 한 항목 — ZSET 순서(점수 내림차순)대로 반환된다. rank 는 어댑터가 채운다(예: Redis 는
 * {@code offset + i + 1}) — "정렬·페이징에서 rank 가 어떻게 도출되는지"를 아는 저장소가 직접 채워야,
 * 응용 서비스의 조립 루프가 저장소별로 분기하지 않고 {@code entry.rank()} 만 읽으면 된다.
 */
public record RankedProduct(long rank, long productId, double score) {
}
