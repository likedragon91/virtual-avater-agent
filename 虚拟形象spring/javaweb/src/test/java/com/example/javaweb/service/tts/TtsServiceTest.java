package com.example.javaweb.service.tts;

import com.example.javaweb.config.TtsProperties;
import com.example.javaweb.dto.request.TtsSynthesizeCommandDTO;
import com.example.javaweb.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TtsServiceTest {

    @Mock
    private TtsProvider provider;

    @Mock
    private TtsAudioCache audioCache;

    @Mock
    private TtsUsageService usageService;

    @InjectMocks
    private TtsService service;

    private TtsProperties properties;

    @BeforeEach
    void setUp() {
        properties = new TtsProperties();
        properties.setEnabled(true);
        properties.setMaxCharsPerRequest(300);
        properties.setDailyCharQuota(5000);
        properties.setCacheTtlSeconds(3600);

        TtsProperties.Volcengine volcengine = properties.getVolcengine();
        volcengine.setApiKey("test-key");
        volcengine.setResourceId("seed-tts-2.0");
        volcengine.setSpeaker("voice-default");
        volcengine.setEndpoint("https://example.com/tts");
        volcengine.setVoices(List.of());

        ReflectionTestUtils.setField(service, "properties", properties);
    }

    private static TtsSynthesizeCommandDTO command(String text, String speaker) {
        TtsSynthesizeCommandDTO dto = new TtsSynthesizeCommandDTO();
        dto.setText(text);
        dto.setSpeaker(speaker);
        return dto;
    }

    @Test
    void rejectsWhenNotConfigured() {
        properties.setEnabled(false);

        assertThatThrownBy(() -> service.synthesize(1L, command("你好", null)))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getCode())
                .isEqualTo("TTS_NOT_CONFIGURED");
    }

    @Test
    void rejectsTooLongText() {
        properties.setMaxCharsPerRequest(5);

        assertThatThrownBy(() -> service.synthesize(1L, command("一二三四五六", null)))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getCode())
                .isEqualTo("TTS_TEXT_TOO_LONG");
    }

    @Test
    void cacheHitDoesNotCallProviderOrConsumeQuota() {
        byte[] cached = new byte[]{1, 2, 3};
        when(audioCache.get(anyString())).thenReturn(cached);

        TtsService.TtsResult result = service.synthesize(1L, command("你好", null));

        assertThat(result.cacheHit()).isTrue();
        assertThat(result.audio().bytes()).containsExactly(1, 2, 3);
        verify(provider, never()).synthesize(anyString(), anyString(), any(), anyString());
        verify(usageService, never()).checkAndConsume(anyLong(), anyInt());
    }

    @Test
    void cacheMissConsumesQuotaAndCachesResult() {
        byte[] audioBytes = new byte[]{4, 5};
        when(audioCache.get(anyString())).thenReturn(null);
        when(provider.synthesize(anyString(), anyString(), any(), anyString()))
                .thenReturn(new TtsAudio(audioBytes, "audio/mpeg", 24000, 1));

        TtsService.TtsResult result = service.synthesize(1L, command("你好", null));

        assertThat(result.cacheHit()).isFalse();
        verify(usageService).checkAndConsume(1L, 2);
        verify(audioCache).put(anyString(), any(byte[].class), anyLong());

        ArgumentCaptor<String> speaker = ArgumentCaptor.forClass(String.class);
        verify(provider).synthesize(anyString(), speaker.capture(), any(), anyString());
        assertThat(speaker.getValue()).isEqualTo("voice-default");
    }

    @Test
    void unknownSpeakerFallsBackToDefault() {
        when(audioCache.get(anyString())).thenReturn(null);
        when(provider.synthesize(anyString(), anyString(), any(), anyString()))
                .thenReturn(new TtsAudio(new byte[]{1}, "audio/mpeg", 24000, 1));

        service.synthesize(1L, command("你好", "not-configured"));

        ArgumentCaptor<String> speaker = ArgumentCaptor.forClass(String.class);
        verify(provider).synthesize(anyString(), speaker.capture(), any(), anyString());
        assertThat(speaker.getValue()).isEqualTo("voice-default");
    }
}
