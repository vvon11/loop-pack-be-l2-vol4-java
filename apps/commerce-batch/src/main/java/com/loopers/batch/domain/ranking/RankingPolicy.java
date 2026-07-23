package com.loopers.batch.domain.ranking;

/** MV 저장 상한이자 Chunk Step 의 chunkSize. 한 상수로 묶어 두 값의 드리프트를 차단한다. */
public final class RankingPolicy {

    public static final int TOP_N = 100;

    private RankingPolicy() {
    }
}
