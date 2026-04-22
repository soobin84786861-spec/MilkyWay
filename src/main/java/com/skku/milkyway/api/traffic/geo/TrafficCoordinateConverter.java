package com.skku.milkyway.api.traffic.geo;

import com.skku.milkyway.api.traffic.domain.GeoCoordinate;
import org.locationtech.proj4j.CRSFactory;
import org.locationtech.proj4j.CoordinateReferenceSystem;
import org.locationtech.proj4j.CoordinateTransform;
import org.locationtech.proj4j.CoordinateTransformFactory;
import org.locationtech.proj4j.ProjCoordinate;
import org.springframework.stereotype.Component;

/**
 * 서울시 SpotInfo의 GRS80TM 좌표를 WGS84 경위도로 변환한다.
 *
 * <p>SpotInfo에서 내려오는 좌표는 실제 값 범위상
 * KGD2002 / Central Belt(EPSG:5181, false northing 500000)에 맞춰 해석하는 쪽이 자연스럽다.</p>
 */
@Component
public class TrafficCoordinateConverter {

    private final CoordinateTransform transform;

    public TrafficCoordinateConverter() {
        CRSFactory crsFactory = new CRSFactory();
        CoordinateReferenceSystem source = crsFactory.createFromParameters(
                "EPSG:5181",
                "+proj=tmerc +lat_0=38 +lon_0=127 +k=1 +x_0=200000 +y_0=500000 +ellps=GRS80 +units=m +no_defs"
        );
        CoordinateReferenceSystem target = crsFactory.createFromParameters(
                "EPSG:4326",
                "+proj=longlat +datum=WGS84 +no_defs"
        );
        this.transform = new CoordinateTransformFactory().createTransform(source, target);
    }

    public GeoCoordinate toWgs84(double grs80tmX, double grs80tmY) {
        ProjCoordinate source = new ProjCoordinate(grs80tmX, grs80tmY);
        ProjCoordinate target = new ProjCoordinate();
        transform.transform(source, target);
        return new GeoCoordinate(target.y, target.x);
    }
}
