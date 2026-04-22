package com.skku.milkyway.api.traffic.controller;

import com.skku.milkyway.api.code.SeoulDistrict;
import com.skku.milkyway.api.traffic.domain.DistrictTrafficAggregate;
import com.skku.milkyway.api.traffic.service.TrafficFacadeService;
import com.skku.milkyway.api.traffic.service.TrafficService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/traffic")
@RequiredArgsConstructor
public class TrafficController {

    private final TrafficFacadeService trafficFacadeService;
    private final TrafficService trafficService;

    @GetMapping("/districts")
    public List<DistrictTrafficAggregate> getDistrictTrafficAggregates(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Integer hour
    ) {
        return trafficFacadeService.getDistrictTrafficAggregates(date, hour);
    }

    @GetMapping("/average")
    public double getCurrentAverageTraffic(@RequestParam SeoulDistrict district) {
        return trafficService.getCurrentAverageTraffic(district);
    }

    @GetMapping("/averages")
    public Map<SeoulDistrict, Double> getAllCurrentAverageTraffic() {
        return trafficService.getAllCurrentAverageTraffic();
    }

    @GetMapping("/average/overall")
    public double getOverallAverageTraffic() {
        return trafficService.getOverallAverageTraffic();
    }
}
