package com.example.javaweb.service;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.javaweb.avatar.AvatarCatalog;
import com.example.javaweb.dto.request.UpdateAvatarConfigCommandDTO;
import com.example.javaweb.dto.response.AvatarCatalogResponseDTO;
import com.example.javaweb.dto.response.AvatarConfigResponseDTO;
import com.example.javaweb.dto.response.AvatarPaletteFieldDTO;
import com.example.javaweb.dto.response.AvatarPresetDTO;
import com.example.javaweb.entity.AvatarConfig;
import com.example.javaweb.entity.AvatarModel;
import com.example.javaweb.exception.BusinessException;
import com.example.javaweb.mapper.AvatarConfigMapper;
import com.example.javaweb.mapper.AvatarModelMapper;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 形象配置读写。config JSON 只保存预设 ID 与配色，素材本身由前端/CDN 提供。
 * 使用 revision 做乐观锁，避免两个窗口同时改形象互相覆盖。
 */
@Service
public class AvatarConfigService {
    private static final Logger log = LoggerFactory.getLogger(AvatarConfigService.class);
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Resource
    private AvatarConfigMapper avatarConfigMapper;

    @Resource
    private AvatarModelMapper avatarModelMapper;

    public AvatarCatalogResponseDTO catalog() {
        AvatarCatalogResponseDTO dto = new AvatarCatalogResponseDTO();
        dto.setDriver(AvatarCatalog.DRIVER_PIXEL);
        dto.setDefaultPresetId(AvatarCatalog.DEFAULT_PRESET_ID);
        dto.setDefaultPalette(new LinkedHashMap<>(AvatarCatalog.DEFAULT_PALETTE));
        dto.setPaletteFields(AvatarCatalog.PALETTE_LABELS.entrySet().stream()
                .map(entry -> new AvatarPaletteFieldDTO(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList()));
        dto.setPresets(AvatarCatalog.PRESETS.stream()
                .map(this::toPresetDto)
                .collect(Collectors.toList()));
        return dto;
    }

    public AvatarConfigResponseDTO get(Long userId) {
        AvatarConfig row = avatarConfigMapper.selectById(userId);
        return row == null ? defaultConfig() : toResponse(row);
    }

    public AvatarConfigResponseDTO update(Long userId, UpdateAvatarConfigCommandDTO command) {
        AvatarConfig row = avatarConfigMapper.selectById(userId);
        int currentRevision = row == null || row.getRevision() == null ? 0 : row.getRevision();
        int requestedRevision = command.getRevision() == null
                ? currentRevision : command.getRevision();
        if (requestedRevision != currentRevision) {
            throw conflict();
        }

        AvatarCatalog.Preset preset = AvatarCatalog.find(command.getPresetId());
        if (preset == null) {
            preset = AvatarCatalog.find(readPresetId(row));
        }
        if (preset == null) {
            preset = AvatarCatalog.find(AvatarCatalog.DEFAULT_PRESET_ID);
        }

        String driver = hasText(command.getDriver())
                ? command.getDriver().trim().toLowerCase()
                : (command.getModelId() != null ? "live2d" : "pixel");
        if (!AvatarCatalog.DRIVER_PIXEL.equals(driver)
                && !"live2d".equals(driver)
                && !"image".equals(driver)) {
            throw new BusinessException("PARAM_ERROR", "不支持的 driver");
        }

        Long modelId = null;
        String modelName = null;
        String modelUrl = null;
        if ("live2d".equals(driver) || "image".equals(driver)) {
            if (command.getModelId() == null) {
                throw new BusinessException("PARAM_ERROR",
                        "image".equals(driver) ? "请选择静态立绘" : "请选择 Live2D 模型");
            }
            AvatarModel model = avatarModelMapper.selectById(command.getModelId());
            String modelDriver = model == null || model.getDriver() == null
                    ? "live2d" : model.getDriver();
            if (model == null || !userId.equals(model.getUserId())
                    || !driver.equals(modelDriver)) {
                throw new BusinessException("NOT_FOUND", "模型不存在");
            }
            modelId = model.getId();
            modelName = model.getName();
            if ("image".equals(driver)) {
                modelUrl = "/api/files/" + model.getFilePath();
            }
        }

        Map<String, String> palette = command.getPalette() == null || command.getPalette().isEmpty()
                ? new LinkedHashMap<>(preset.palette())
                : AvatarCatalog.sanitize(command.getPalette());

        JSONObject config = new JSONObject();
        config.set("driver", driver);
        config.set("presetId", preset.id());
        config.set("palette", palette);
        if (modelId != null) {
            config.set("modelId", modelId);
            config.set("modelName", modelName);
        }
        if (modelUrl != null) {
            config.set("modelUrl", modelUrl);
        }

        if (row == null) {
            AvatarConfig insert = new AvatarConfig();
            insert.setUserId(userId);
            insert.setDriver(driver);
            insert.setConfig(config.toString());
            insert.setRevision(1);
            try {
                avatarConfigMapper.insert(insert);
            } catch (DuplicateKeyException e) {
                // 两个窗口同时首次保存，后到者视为版本冲突
                throw conflict();
            }
        } else {
            LambdaUpdateWrapper<AvatarConfig> update = new LambdaUpdateWrapper<AvatarConfig>()
                    .eq(AvatarConfig::getUserId, userId)
                    .eq(AvatarConfig::getRevision, currentRevision)
                    .set(AvatarConfig::getDriver, driver)
                    .set(AvatarConfig::getConfig, config.toString())
                    .set(AvatarConfig::getRevision, currentRevision + 1)
                    .set(AvatarConfig::getUpdatedAt, LocalDateTime.now());
            int affected = avatarConfigMapper.update(null, update);
            if (affected == 0) {
                throw conflict();
            }
        }
        return get(userId);
    }

    private AvatarConfigResponseDTO defaultConfig() {
        AvatarConfigResponseDTO dto = new AvatarConfigResponseDTO();
        dto.setDriver(AvatarCatalog.DRIVER_PIXEL);
        dto.setPresetId(AvatarCatalog.DEFAULT_PRESET_ID);
        dto.setPalette(new LinkedHashMap<>(AvatarCatalog.DEFAULT_PALETTE));
        dto.setRevision(0);
        return dto;
    }

    private AvatarConfigResponseDTO toResponse(AvatarConfig row) {
        String presetId = AvatarCatalog.DEFAULT_PRESET_ID;
        Map<String, String> palette = AvatarCatalog.sanitize(null);
        String driver = hasText(row.getDriver()) ? row.getDriver() : AvatarCatalog.DRIVER_PIXEL;
        Long modelId = null;
        String modelName = null;
        String modelUrl = null;

        try {
            JSONObject config = JSONUtil.parseObj(row.getConfig());
            String configDriver = config.getStr("driver");
            if (hasText(configDriver)) {
                driver = configDriver;
            }
            modelId = config.getLong("modelId");
            modelName = config.getStr("modelName");
            modelUrl = config.getStr("modelUrl");

            String storedPresetId = config.getStr("presetId");
            AvatarCatalog.Preset storedPreset = AvatarCatalog.find(storedPresetId);
            if (storedPreset != null) {
                presetId = storedPreset.id();
            }

            JSONObject storedPalette = config.getJSONObject("palette");
            if (storedPalette != null) {
                Map<String, String> paletteMap = new LinkedHashMap<>();
                for (String key : storedPalette.keySet()) {
                    paletteMap.put(key, storedPalette.getStr(key));
                }
                palette = AvatarCatalog.sanitize(paletteMap);
            } else if (storedPreset != null) {
                palette = new LinkedHashMap<>(storedPreset.palette());
            }
        } catch (Exception e) {
            log.warn("parse avatar config failed, userId={}", row.getUserId(), e);
        }

        if (("live2d".equals(driver) || "image".equals(driver)) && modelId == null) {
            driver = AvatarCatalog.DRIVER_PIXEL;
            modelUrl = null;
        }
        if ("pixel".equals(driver)) {
            modelUrl = null;
        }
        if ("image".equals(driver) && modelUrl == null) {
            // 兼容早期只存了 modelId 的配置
            AvatarModel model = avatarModelMapper.selectById(modelId);
            if (model != null && row.getUserId().equals(model.getUserId())) {
                modelUrl = "/api/files/" + model.getFilePath();
            }
        }

        AvatarConfigResponseDTO dto = new AvatarConfigResponseDTO();
        dto.setDriver(driver);
        dto.setPresetId(presetId);
        dto.setPalette(palette);
        dto.setModelId(modelId);
        dto.setModelName(modelName);
        dto.setModelUrl(modelUrl);
        dto.setRevision(row.getRevision() == null ? 1 : row.getRevision());
        dto.setUpdatedAt(row.getUpdatedAt() == null ? null
                : row.getUpdatedAt().format(DATE_TIME_FORMATTER));
        return dto;
    }

    private String readPresetId(AvatarConfig row) {
        if (row == null || row.getConfig() == null) {
            return null;
        }
        try {
            return JSONUtil.parseObj(row.getConfig()).getStr("presetId");
        } catch (Exception e) {
            return null;
        }
    }

    private AvatarPresetDTO toPresetDto(AvatarCatalog.Preset preset) {
        AvatarPresetDTO dto = new AvatarPresetDTO();
        dto.setId(preset.id());
        dto.setName(preset.name());
        dto.setDescription(preset.description());
        dto.setPalette(new LinkedHashMap<>(preset.palette()));
        return dto;
    }

    private static BusinessException conflict() {
        return new BusinessException("AVATAR_CONFIG_CONFLICT",
                "形象配置已在其他窗口更新，请刷新后重试");
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
