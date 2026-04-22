package com.skku.milkyway.api.traffic.controller;

import com.skku.milkyway.api.code.SeoulDistrict;
import com.skku.milkyway.api.traffic.domain.DistrictTrafficAggregate;
import com.skku.milkyway.api.traffic.service.TrafficFacadeService;
import com.skku.milkyway.api.traffic.service.TrafficService;
import com.skku.milkyway.api.traffic.sync.TrafficSpotMappingSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 자치구별 교통량 집계 결과를 조회하는 API 컨트롤러.
 */
@RestController
@RequestMapping("/api/traffic")
@RequiredArgsConstructor
public class TrafficController {

    private final TrafficFacadeService trafficFacadeService;
    private final TrafficService trafficService;
    private final TrafficSpotMappingSyncService trafficSpotMappingSyncService;

    /**
     * 특정 날짜/시간의 자치구별 교통량 집계 결과를 반환한다.
     */
    @GetMapping("/districts")
    public List<DistrictTrafficAggregate> getDistrictTrafficAggregates(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Integer hour
    ) {
        return trafficFacadeService.getDistrictTrafficAggregates(date, hour);
    }

    /**
     * 특정 자치구의 현재 평균 통행량을 반환한다.
     */
    @GetMapping("/average")
    public double getCurrentAverageTraffic(@RequestParam SeoulDistrict district) {
        return trafficService.getCurrentAverageTraffic(district);
    }

    /**
     * 모든 자치구의 현재 평균 통행량을 반환한다.
     */
    @GetMapping("/averages")
    public Map<SeoulDistrict, Double> getAllCurrentAverageTraffic() {
        return trafficService.getAllCurrentAverageTraffic();
    }

    /**
     * 모든 자치구 평균 통행량의 전체 평균값을 반환한다.
     */
    @GetMapping("/average/overall")
    public double getOverallAverageTraffic() {
        return trafficService.getOverallAverageTraffic();
    }

    /**
     * SpotInfo와 reverse geocoding 결과를 이용해 spot_num -> 자치구 매핑 파일을 갱신한다.
     */
    @GetMapping(value = "/mappings/sync")
    public Map<String, String> syncSpotDistrictMappings() {
        return trafficSpotMappingSyncService.syncSpotDistrictMappings();
    }
}
