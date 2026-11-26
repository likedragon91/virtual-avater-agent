package com.example.javaweb.controller;

import com.example.javaweb.common.Result;
import com.example.javaweb.dto.request.TtsSynthesizeCommandDTO;
import com.example.javaweb.dto.response.TtsConfigResponseDTO;
import com.example.javaweb.security.CurrentUserResolver;
import com.example.javaweb.service.tts.TtsService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * 语音合成接口。密钥与上游地址不会返回给客户端。
 */
@RestController
@RequestMapping("/api/tts")
public class TtsController {

    @Resource
    private TtsService ttsService;

    @Resource
    private CurrentUserResolver currentUserResolver;

    @GetMapping("/config")
    public Result<TtsConfigResponseDTO> config(Authentication authentication) {
        Long userId = currentUserResolver.requireUserId(authentication);
        return Result.success(ttsService.config(userId));
    }

    @PostMapping("/synthesize")
    public ResponseEntity<byte[]> synthesize(
            @Valid @RequestBody TtsSynthesizeCommandDTO command,
            Authentication authentication) {
        Long userId = currentUserResolver.requireUserId(authentication);
        TtsService.TtsResult result = ttsService.synthesize(userId, command);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(result.audio().contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
                .header("X-TTS-Cache", result.cacheHit() ? "HIT" : "MISS")
                .header("X-Audio-Sample-Rate", String.valueOf(result.audio().sampleRate()))
                .header("X-Audio-Channels", String.valueOf(result.audio().channels()))
                .body(result.audio().bytes());
    }
}
