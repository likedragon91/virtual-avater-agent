package com.example.javaweb.service.tts;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class TtsChunkParserTest {

    private static String line(int code, String base64) {
        return "{\"code\":" + code + ",\"data\":\"" + base64 + "\"}";
    }

    @Test
    void decodesAudioChunk() {
        byte[] audio = new byte[]{1, 2, 3, 4};
        TtsChunkParser parser = new TtsChunkParser(1024);

        byte[] chunk = parser.acceptLine(line(0, Base64.getEncoder().encodeToString(audio)));

        assertThat(chunk).containsExactly(1, 2, 3, 4);
        assertThat(parser.audio()).containsExactly(1, 2, 3, 4);
        assertThat(parser.hasError()).isFalse();
    }

    @Test
    void acceptsDataPrefixAndFinishCode() {
        byte[] audio = new byte[]{9, 8};
        TtsChunkParser parser = new TtsChunkParser(1024);

        parser.acceptLine("data: " + line(0, Base64.getEncoder().encodeToString(audio)));
        parser.acceptLine("data: " + line(20000000, ""));

        assertThat(parser.audio()).containsExactly(9, 8);
        assertThat(parser.isFinished()).isTrue();
    }

    @Test
    void recordsUpstreamError() {
        TtsChunkParser parser = new TtsChunkParser(1024);
        parser.acceptLine("{\"code\":55000000,\"message\":\"speaker mismatch\"}");

        assertThat(parser.hasError()).isTrue();
        assertThat(parser.errorCode()).isEqualTo("55000000");
        assertThat(parser.errorMessage()).contains("speaker");
    }

    @Test
    void ignoresNonJsonAndHeartbeatLines() {
        TtsChunkParser parser = new TtsChunkParser(1024);

        parser.acceptLine("");
        parser.acceptLine(": ping");
        parser.acceptLine("event: message");

        assertThat(parser.hasError()).isFalse();
        assertThat(parser.audio()).isEmpty();
    }

    @Test
    void rejectsOversizedAudio() {
        TtsChunkParser parser = new TtsChunkParser(1024);
        parser.acceptLine(line(0, Base64.getEncoder().encodeToString(new byte[2048])));

        assertThat(parser.hasError()).isTrue();
        assertThat(parser.errorCode()).isEqualTo("TTS_AUDIO_TOO_LARGE");
    }
}
