package com.skku.milkyway.api.cctv.controller;

import com.skku.milkyway.api.cctv.response.PublicCctvResponse;
import com.skku.milkyway.api.cctv.service.PublicCctvService;
import com.skku.milkyway.api.cctv.service.PublicCctvService.ViewBounds;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/cctv")
@RequiredArgsConstructor
public class CctvController {

    private final PublicCctvService publicCctvService;

    @GetMapping
    public List<PublicCctvResponse> getAll() {
        return publicCctvService.getAll();
    }

    @GetMapping("/{cctvId}/stream")
    public ResponseEntity<String> proxyStream(
            @PathVariable String cctvId,
            @RequestParam(required = false) Double minX,
            @RequestParam(required = false) Double minY,
            @RequestParam(required = false) Double maxX,
            @RequestParam(required = false) Double maxY
    ) {
        String html = publicCctvService.proxyStreamPage(cctvId, new ViewBounds(minX, minY, maxX, maxY));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "text/html;charset=UTF-8")
                .body(html);
    }
}
