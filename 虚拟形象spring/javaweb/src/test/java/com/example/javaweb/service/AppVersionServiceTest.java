package com.example.javaweb.service;

import com.example.javaweb.dto.response.AppVersionResponseDTO;
import com.example.javaweb.entity.AppVersion;
import com.example.javaweb.exception.BusinessException;
import com.example.javaweb.mapper.AppVersionMapper;
import com.example.javaweb.mapper.NotificationMapper;
import com.example.javaweb.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppVersionServiceTest {

    @Mock
    private AppVersionMapper appVersionMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private NotificationMapper notificationMapper;

    @Mock
    private NotificationService notificationService;

    @Mock
    private NotificationPushService notificationPushService;

    @Mock
    private FileStorageService fileStorageService;

    @InjectMocks
    private AppVersionService service;

    private static AppVersion row(String version, String filePath) {
        AppVersion row = new AppVersion();
        row.setId(1L);
        row.setVersion(version);
        row.setFilePath(filePath);
        row.setFileName("虚拟形象 Setup " + version + ".exe");
        row.setFileSize(1024L);
        return row;
    }

    @Test
    void comparesVersionsNumerically() {
        assertThat(AppVersionService.compareVersions("0.1.10", "0.1.9")).isPositive();
        assertThat(AppVersionService.compareVersions("0.2.0", "0.1.99")).isPositive();
        assertThat(AppVersionService.compareVersions("1.0", "1.0.0")).isZero();
        assertThat(AppVersionService.compareVersions("0.1.0", "0.1.1")).isNegative();
    }

    @Test
    void latestMapsToPublicDownloadUrl() {
        when(appVersionMapper.selectOne(any())).thenReturn(row("0.1.1", "updates/a.exe"));

        AppVersionResponseDTO dto = service.latest();

        assertThat(dto.getVersion()).isEqualTo("0.1.1");
        assertThat(dto.getDownloadUrl()).isEqualTo("/api/files/updates/a.exe");
    }

    @Test
    void rejectsVersionNotGreaterThanLatest() {
        when(appVersionMapper.selectCount(any())).thenReturn(0L);
        when(appVersionMapper.selectOne(any())).thenReturn(row("0.2.0", "updates/a.exe"));

        MockMultipartFile file = new MockMultipartFile(
                "file", "setup.exe", "application/octet-stream", new byte[]{0x4D, 0x5A});

        assertThatThrownBy(() -> service.publish(1L, "0.1.9", "notes", file, false))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getCode())
                .isEqualTo("VERSION_TOO_OLD");
        verifyNoInteractions(fileStorageService);
    }

    @Test
    void publishesInstallerAndReturnsVersion() {
        when(appVersionMapper.selectCount(any())).thenReturn(0L);
        when(appVersionMapper.selectOne(any())).thenReturn(null);
        when(fileStorageService.storeInstaller(any())).thenReturn(
                new FileStorageService.StoredFile(
                        "updates/x.exe", "/api/files/updates/x.exe", 2048, "setup.exe"));
        when(appVersionMapper.insert(any(AppVersion.class))).thenAnswer(invocation -> {
            AppVersion inserted = invocation.getArgument(0);
            inserted.setId(7L);
            return 1;
        });

        MockMultipartFile file = new MockMultipartFile(
                "file", "setup.exe", "application/octet-stream", new byte[]{0x4D, 0x5A});
        AppVersionResponseDTO dto = service.publish(1L, "0.1.1", "修复若干问题", file, false);

        assertThat(dto.getId()).isEqualTo(7L);
        assertThat(dto.getVersion()).isEqualTo("0.1.1");
        assertThat(dto.getDownloadUrl()).isEqualTo("/api/files/updates/x.exe");
        assertThat(dto.getNotes()).isEqualTo("修复若干问题");
        verify(appVersionMapper).insert(any(AppVersion.class));
    }
}
