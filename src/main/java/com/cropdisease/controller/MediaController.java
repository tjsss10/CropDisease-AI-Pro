package com.cropdisease.controller;

import com.cropdisease.repository.MediaRepository;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

/** Serves leaf photos and heatmaps stored in the MySQL "media" table. */
@RestController
public class MediaController {

    private final MediaRepository media;

    public MediaController(MediaRepository media) {
        this.media = media;
    }

    @GetMapping("/media/{id:.+}")
    public ResponseEntity<byte[]> get(@PathVariable String id) {
        return media.findById(id)
                .map(m -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(m.getContentType()))
                        .cacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePrivate())
                        .body(m.getData()))
                .orElse(ResponseEntity.notFound().build());
    }
}
