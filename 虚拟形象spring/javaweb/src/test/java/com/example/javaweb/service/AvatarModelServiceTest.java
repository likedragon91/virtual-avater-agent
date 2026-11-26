package com.example.javaweb.service;

import com.example.javaweb.dto.response.AvatarModelResponseDTO;
import com.example.javaweb.entity.AvatarModel;
import com.example.javaweb.mapper.AvatarModelMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AvatarModelServiceTest {

    @Mock
    private AvatarModelMapper avatarModelMapper;

    @Mock
    private FileStorageService fileStorageService;

    @InjectMocks
    private AvatarModelService service;

    @Test
    void uploadImageStoresDriverAndPublicUrl() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "从雨.jpg", "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
        when(fileStorageService.storeImage(file, "avatar-image"))
                .thenReturn(new FileStorageService.StoredFile(
                        "avatar-image/abc.jpg", "/api/files/avatar-image/abc.jpg", 99 * 1024, "从雨.jpg"));
        when(avatarModelMapper.insert(any(AvatarModel.class))).thenAnswer(invocation -> {
            AvatarModel model = invocation.getArgument(0);
            model.setId(12L);
            return 1;
        });

        AvatarModelResponseDTO dto = service.uploadImage(1L, file);

        assertThat(dto.getId()).isEqualTo(12L);
        assertThat(dto.getDriver()).isEqualTo("image");
        assertThat(dto.getUrl()).isEqualTo("/api/files/avatar-image/abc.jpg");
        assertThat(dto.getName()).isEqualTo("从雨.jpg");
    }
}
