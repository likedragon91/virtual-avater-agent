package com.example.javaweb.service;

import com.example.javaweb.config.UploadProperties;
import com.example.javaweb.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileStorageServiceTest {

    private static final byte[] PNG_BYTES = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0
    };
    private static final byte[] ZIP_BYTES = {
            0x50, 0x4B, 0x03, 0x04, 0, 0, 0, 0
    };

    @TempDir
    Path tempDir;

    private FileStorageService service;
    private UploadProperties properties;

    @BeforeEach
    void setUp() {
        properties = new UploadProperties();
        properties.setDir(tempDir.toString());
        properties.setPublicPath("/api/files");
        properties.setMaxImageBytes(20L * 1024 * 1024);
        properties.setMaxModelBytes(20L * 1024 * 1024);

        service = new FileStorageService();
        ReflectionTestUtils.setField(service, "properties", properties);
        service.init();
    }

    @Test
    void storesPngAndReturnsPublicUrl() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.png", "image/png", PNG_BYTES);

        FileStorageService.StoredFile stored = service.storeImage(file, "avatar");

        assertThat(stored.relativePath()).startsWith("avatar/").endsWith(".png");
        assertThat(stored.url()).startsWith("/api/files/avatar/");
        assertThat(stored.size()).isEqualTo(PNG_BYTES.length);
        assertThat(service.resolve(stored.relativePath())).exists();
    }

    @Test
    void storesZipForLive2d() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "model.zip", "application/zip", ZIP_BYTES);

        FileStorageService.StoredFile stored = service.storeZip(file, "live2d/1");

        assertThat(stored.relativePath()).startsWith("live2d/1/").endsWith(".zip");
        assertThat(service.resolve(stored.relativePath())).exists();
    }

    @Test
    void rejectsMismatchedMagicBytes() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "fake.png", "image/png", ZIP_BYTES);

        assertThatThrownBy(() -> service.storeImage(file, "avatar"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getCode())
                .isEqualTo("PARAM_ERROR");
    }

    @Test
    void rejectsOversizedFile() {
        properties.setMaxImageBytes(4);
        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.png", "image/png", PNG_BYTES);

        assertThatThrownBy(() -> service.storeImage(file, "avatar"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getCode())
                .isEqualTo("FILE_TOO_LARGE");
    }

    @Test
    void rejectsPathTraversal() {
        assertThatThrownBy(() -> service.resolve("../../etc/passwd"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getCode())
                .isEqualTo("FORBIDDEN");
    }
}
