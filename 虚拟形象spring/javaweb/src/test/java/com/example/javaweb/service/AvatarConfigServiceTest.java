package com.example.javaweb.service;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.example.javaweb.dto.request.UpdateAvatarConfigCommandDTO;
import com.example.javaweb.dto.response.AvatarConfigResponseDTO;
import com.example.javaweb.entity.AvatarConfig;
import com.example.javaweb.entity.AvatarModel;
import com.example.javaweb.exception.BusinessException;
import com.example.javaweb.mapper.AvatarConfigMapper;
import com.example.javaweb.mapper.AvatarModelMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AvatarConfigServiceTest {

    @Mock
    private AvatarConfigMapper avatarConfigMapper;

    @Mock
    private AvatarModelMapper avatarModelMapper;

    @InjectMocks
    private AvatarConfigService service;

    private static AvatarConfig row(int revision, String config) {
        AvatarConfig row = new AvatarConfig();
        row.setUserId(1L);
        row.setDriver("pixel");
        row.setRevision(revision);
        row.setConfig(config);
        return row;
    }

    @Test
    void returnsDefaultsWhenNoRow() {
        when(avatarConfigMapper.selectById(1L)).thenReturn(null);

        AvatarConfigResponseDTO dto = service.get(1L);

        assertThat(dto.getRevision()).isZero();
        assertThat(dto.getPresetId()).isEqualTo("preset_default");
        assertThat(dto.getPalette()).containsEntry("hair", "#6b4b3e");
    }

    @Test
    void readsStoredPaletteAndPreset() {
        when(avatarConfigMapper.selectById(1L)).thenReturn(row(3,
                "{\"presetId\":\"preset_cool\",\"palette\":{\"hair\":\"#112233\",\"unknown\":\"#ffffff\"}}"));

        AvatarConfigResponseDTO dto = service.get(1L);

        assertThat(dto.getRevision()).isEqualTo(3);
        assertThat(dto.getPresetId()).isEqualTo("preset_cool");
        assertThat(dto.getPalette()).containsEntry("hair", "#112233");
        assertThat(dto.getPalette()).doesNotContainKey("unknown");
    }

    @Test
    void rejectsStaleRevision() {
        when(avatarConfigMapper.selectById(1L)).thenReturn(row(2,
                "{\"presetId\":\"preset_default\",\"palette\":{}}"));

        UpdateAvatarConfigCommandDTO command = new UpdateAvatarConfigCommandDTO();
        command.setRevision(1);

        assertThatThrownBy(() -> service.update(1L, command))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getCode())
                .isEqualTo("AVATAR_CONFIG_CONFLICT");
        verify(avatarConfigMapper, never()).update(any(), any());
    }

    @Test
    void firstSaveInsertsPresetPalette() {
        when(avatarConfigMapper.selectById(1L)).thenReturn(null);

        UpdateAvatarConfigCommandDTO command = new UpdateAvatarConfigCommandDTO();
        command.setPresetId("preset_warm");
        command.setRevision(0);

        service.update(1L, command);

        ArgumentCaptor<AvatarConfig> captor = ArgumentCaptor.forClass(AvatarConfig.class);
        verify(avatarConfigMapper).insert(captor.capture());
        AvatarConfig inserted = captor.getValue();
        assertThat(inserted.getRevision()).isEqualTo(1);
        assertThat(inserted.getDriver()).isEqualTo("pixel");

        JSONObject config = JSONUtil.parseObj(inserted.getConfig());
        assertThat(config.getStr("presetId")).isEqualTo("preset_warm");
        assertThat(config.getJSONObject("palette").getStr("hair")).isEqualTo("#7a4a2f");
    }

    @Test
    void invalidPaletteValuesFallBackToDefaults() {
        when(avatarConfigMapper.selectById(1L)).thenReturn(null);

        UpdateAvatarConfigCommandDTO command = new UpdateAvatarConfigCommandDTO();
        command.setPresetId("preset_default");
        command.setPalette(Map.of("hair", "not-a-color", "evil", "#ffffff"));
        command.setRevision(0);

        service.update(1L, command);

        ArgumentCaptor<AvatarConfig> captor = ArgumentCaptor.forClass(AvatarConfig.class);
        verify(avatarConfigMapper).insert(captor.capture());
        JSONObject config = JSONUtil.parseObj(captor.getValue().getConfig());
        JSONObject palette = config.getJSONObject("palette");
        assertThat(palette.getStr("hair")).isEqualTo("#6b4b3e");
        assertThat(palette.containsKey("evil")).isFalse();
    }

    @Test
    void savesOwnedLive2dModel() {
        when(avatarConfigMapper.selectById(1L)).thenReturn(null);
        AvatarModel model = new AvatarModel();
        model.setId(9L);
        model.setUserId(1L);
        model.setName("测试模型");
        model.setDriver("live2d");
        when(avatarModelMapper.selectById(9L)).thenReturn(model);

        UpdateAvatarConfigCommandDTO command = new UpdateAvatarConfigCommandDTO();
        command.setDriver("live2d");
        command.setModelId(9L);
        command.setRevision(0);

        service.update(1L, command);

        ArgumentCaptor<AvatarConfig> captor = ArgumentCaptor.forClass(AvatarConfig.class);
        verify(avatarConfigMapper).insert(captor.capture());
        AvatarConfig inserted = captor.getValue();
        assertThat(inserted.getDriver()).isEqualTo("live2d");

        JSONObject config = JSONUtil.parseObj(inserted.getConfig());
        assertThat(config.getStr("driver")).isEqualTo("live2d");
        assertThat(config.getLong("modelId")).isEqualTo(9L);
        assertThat(config.getStr("modelName")).isEqualTo("测试模型");
    }

    @Test
    void rejectsLive2dModelOfAnotherUser() {
        when(avatarConfigMapper.selectById(1L)).thenReturn(null);
        AvatarModel model = new AvatarModel();
        model.setId(9L);
        model.setUserId(2L);
        model.setDriver("live2d");
        when(avatarModelMapper.selectById(9L)).thenReturn(model);

        UpdateAvatarConfigCommandDTO command = new UpdateAvatarConfigCommandDTO();
        command.setDriver("live2d");
        command.setModelId(9L);
        command.setRevision(0);

        assertThatThrownBy(() -> service.update(1L, command))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getCode())
                .isEqualTo("NOT_FOUND");
    }

    @Test
    void savesOwnedStaticImage() {
        when(avatarConfigMapper.selectById(1L)).thenReturn(null);
        AvatarModel model = new AvatarModel();
        model.setId(10L);
        model.setUserId(1L);
        model.setName("立绘.png");
        model.setDriver("image");
        model.setFilePath("avatar-image/abc.png");
        when(avatarModelMapper.selectById(10L)).thenReturn(model);

        UpdateAvatarConfigCommandDTO command = new UpdateAvatarConfigCommandDTO();
        command.setDriver("image");
        command.setModelId(10L);
        command.setRevision(0);

        service.update(1L, command);

        ArgumentCaptor<AvatarConfig> captor = ArgumentCaptor.forClass(AvatarConfig.class);
        verify(avatarConfigMapper).insert(captor.capture());
        AvatarConfig inserted = captor.getValue();
        assertThat(inserted.getDriver()).isEqualTo("image");

        JSONObject config = JSONUtil.parseObj(inserted.getConfig());
        assertThat(config.getStr("driver")).isEqualTo("image");
        assertThat(config.getLong("modelId")).isEqualTo(10L);
        assertThat(config.getStr("modelUrl")).isEqualTo("/api/files/avatar-image/abc.png");
    }

    @Test
    void catalogExposesPresetsAndPaletteFields() {
        var catalog = service.catalog();

        assertThat(catalog.getDefaultPresetId()).isEqualTo("preset_default");
        assertThat(catalog.getPresets()).hasSizeGreaterThanOrEqualTo(4);
        assertThat(catalog.getPaletteFields()).extracting("key")
                .contains("hair", "top", "bottom", "shoes", "skin", "background");
    }
}
