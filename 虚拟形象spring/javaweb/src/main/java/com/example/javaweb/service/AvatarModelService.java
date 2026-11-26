package com.example.javaweb.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.javaweb.dto.response.AvatarModelResponseDTO;
import com.example.javaweb.entity.AvatarModel;
import com.example.javaweb.exception.BusinessException;
import com.example.javaweb.mapper.AvatarModelMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户 Live2D 模型包管理。zip 由 FileStorageService 校验并落盘，
 * 下载必须校验归属，避免通过 id 读取他人模型。
 */
@Service
public class AvatarModelService {
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Resource
    private AvatarModelMapper avatarModelMapper;

    @Resource
    private FileStorageService fileStorageService;

    public List<AvatarModelResponseDTO> list(Long userId) {
        List<AvatarModel> rows = avatarModelMapper.selectList(
                new LambdaQueryWrapper<AvatarModel>()
                        .eq(AvatarModel::getUserId, userId)
                        .orderByDesc(AvatarModel::getId));
        return rows.stream().map(this::toResponse).collect(Collectors.toList());
    }

    public AvatarModelResponseDTO upload(Long userId, MultipartFile file) {
        FileStorageService.StoredFile stored =
                fileStorageService.storeZip(file, "live2d/" + userId);
        AvatarModel model = new AvatarModel();
        model.setUserId(userId);
        model.setName(truncateName(stored.originalName()));
        model.setDriver("live2d");
        model.setFilePath(stored.relativePath());
        model.setFileSize(stored.size());
        model.setCreatedAt(LocalDateTime.now());
        avatarModelMapper.insert(model);
        return toResponse(model);
    }

    /**
     * 单张图片作为静态立绘。文件存在公开图片目录，URL 由 FileController 提供；
     * 不能用它伪造 Live2D（缺少 moc3），所以单独标记 driver=image。
     */
    public AvatarModelResponseDTO uploadImage(Long userId, MultipartFile file) {
        FileStorageService.StoredFile stored =
                fileStorageService.storeImage(file, "avatar-image");
        AvatarModel model = new AvatarModel();
        model.setUserId(userId);
        model.setName(truncateName(stored.originalName()));
        model.setDriver("image");
        model.setFilePath(stored.relativePath());
        model.setFileSize(stored.size());
        model.setCreatedAt(LocalDateTime.now());
        avatarModelMapper.insert(model);
        return toResponse(model);
    }

    public AvatarModel requireOwned(Long userId, Long id) {
        AvatarModel model = id == null ? null : avatarModelMapper.selectById(id);
        if (model == null || !model.getUserId().equals(userId)) {
            throw new BusinessException("NOT_FOUND", "模型不存在");
        }
        return model;
    }

    public org.springframework.core.io.Resource file(Long userId, Long id) {
        AvatarModel model = requireOwned(userId, id);
        return fileStorageService.load(model.getFilePath());
    }

    public void delete(Long userId, Long id) {
        AvatarModel model = requireOwned(userId, id);
        avatarModelMapper.deleteById(id);
        fileStorageService.delete(model.getFilePath());
    }

    private AvatarModelResponseDTO toResponse(AvatarModel model) {
        AvatarModelResponseDTO dto = new AvatarModelResponseDTO();
        dto.setId(model.getId());
        dto.setName(model.getName());
        dto.setDriver(model.getDriver());
        if ("image".equals(model.getDriver())) {
            dto.setUrl("/api/files/" + model.getFilePath());
        }
        dto.setSize(model.getFileSize());
        dto.setCreatedAt(model.getCreatedAt() == null ? null
                : model.getCreatedAt().format(DATE_TIME_FORMATTER));
        return dto;
    }

    private static String truncateName(String name) {
        String value = name == null || name.isBlank() ? "Live2D 模型" : name.trim();
        return value.length() <= 120 ? value : value.substring(0, 120);
    }
}
