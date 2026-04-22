package com.skku.milkyway.api.traffic.store;

import com.skku.milkyway.api.traffic.domain.DistrictTrafficAggregate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 자치구별 교통량 집계 스냅샷을 인메모리로 보관하는 저장소.
 *
 * <p>같은 날짜/시간대 요청이 반복될 때 외부 API를 다시 호출하지 않도록 캐시 역할을 한다.</p>
 */
@Component
public class TrafficSnapshotStore {

    private record CacheKey(LocalDate date, Integer hour) {}

    private record CachedValue(List<DistrictTrafficAggregate> data, long timestamp) {
        boolean isExpired(long ttlMs) {
            return System.currentTimeMillis() - timestamp > ttlMs;
        }
    }

    private final ConcurrentHashMap<CacheKey, CachedValue> cache = new ConcurrentHashMap<>();

    /**
     * 캐시 유효 시간이 남아 있는 경우에만 저장된 스냅샷을 반환한다.
     */
    public List<DistrictTrafficAggregate> get(LocalDate date, Integer hour, long ttlMs) {
        CachedValue cached = cache.get(new CacheKey(date, hour));
        if (cached == null || cached.isExpired(ttlMs)) {
            return null;
        }
        return cached.data();
    }

    /**
     * 특정 날짜/시간 기준 집계 결과를 캐시에 저장한다.
     */
    public void put(LocalDate date, Integer hour, List<DistrictTrafficAggregate> data) {
        cache.put(new CacheKey(date, hour), new CachedValue(List.copyOf(data), System.currentTimeMillis()));
    }
}
