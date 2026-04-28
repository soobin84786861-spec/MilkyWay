package com.skku.milkyway.api.cctv.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicCctvResponse {
    private String name;
    private double latitude;
    private double longitude;
    private String cctvId;
    private String streamUrl;
}
