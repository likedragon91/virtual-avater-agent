package com.example.javaweb.service;

import cn.hutool.json.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.javaweb.dto.response.AppVersionResponseDTO;
import com.example.javaweb.dto.response.NotificationResponseDTO;
import com.example.javaweb.entity.AppVersion;
import com.example.javaweb.entity.Notification;
import com.example.javaweb.entity.User;
import com.example.javaweb.enums.UserStatus;
import com.example.javaweb.exception.BusinessException;
import com.example.javaweb.mapper.AppVersionMapper;
import com.example.javaweb.mapper.NotificationMapper;
import com.example.javaweb.mapper.UserMapper;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 桌面端安装包发布。上传即发布，可选向所有启用用户写入并实时推送 APP_UPDATE 通知。
 */
@Service
public class AppVersionService {
    private static final Logger log = LoggerFactory.getLogger(AppVersionService.class);
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Pattern VERSION_PATTERN = Pattern.compile("^[0-9]+(\\.[0-9]+){1,3}$");

    @Resource
    private AppVersionMapper appVersionMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private NotificationMapper notificationMapper;

    @Resource
    private NotificationService notificationService;

    @Resource
    private NotificationPushService notificationPushService;

    @Resource
    private FileStorageService fileStorageService;

    public AppVersionResponseDTO latest() {
        AppVersion row = appVersionMapper.selectOne(new LambdaQueryWrapper<AppVersion>()
                .orderByDesc(AppVersion::getId)
                .last("LIMIT 1"));
        return row == null ? null : toDto(row);
    }

    public List<AppVersionResponseDTO> list() {
        List<AppVersion> rows = appVersionMapper.selectList(
                new LambdaQueryWrapper<AppVersion>().orderByDesc(AppVersion::getId));
        List<AppVersionResponseDTO> result = new ArrayList<>();
        for (AppVersion row : rows) {
            result.add(toDto(row));
        }
        return result;
    }

    public AppVersionResponseDTO publish(Long adminId, String version, String notes,
                                         MultipartFile file, boolean notify) {
        String normalizedVersion = version == null ? "" : version.trim();
        if (!VERSION_PATTERN.matcher(normalizedVersion).matches()) {
            throw new BusinessException("PARAM_ERROR", "版本号格式应为 0.1.1");
        }
        Long duplicate = appVersionMapper.selectCount(new LambdaQueryWrapper<AppVersion>()
                .eq(AppVersion::getVersion, normalizedVersion));
        if (duplicate != null && duplicate > 0) {
            throw new BusinessException("VERSION_EXISTS", "该版本号已发布");
        }
        AppVersion latest = appVersionMapper.selectOne(new LambdaQueryWrapper<AppVersion>()
                .orderByDesc(AppVersion::getId)
                .last("LIMIT 1"));
        if (latest != null && compareVersions(normalizedVersion, latest.getVersion()) <= 0) {
            throw new BusinessException("VERSION_TOO_OLD",
                    "新版本号必须大于当前最新版本 " + latest.getVersion());
        }

        FileStorageService.StoredFile stored = fileStorageService.storeInstaller(file);
        AppVersion row = new AppVersion();
        row.setVersion(normalizedVersion);
        row.setNotes(notes == null ? null : notes.trim());
        row.setFilePath(stored.relativePath());
        row.setFileName(stored.originalName());
        row.setFileSize(stored.size());
        row.setCreatedBy(adminId);
        row.setCreatedAt(LocalDateTime.now());
        try {
            appVersionMapper.insert(row);
        } catch (DuplicateKeyException e) {
            fileStorageService.delete(stored.relativePath());
            throw new BusinessException("VERSION_EXISTS", "该版本号已发布");
        }

        if (notify) {
            broadcastUpdate(row);
        }
        return toDto(row);
    }

    public void delete(Long id) {
        AppVersion row = id == null ? null : appVersionMapper.selectById(id);
        if (row == null) {
            throw new BusinessException("NOT_FOUND", "版本不存在");
        }
        AppVersion latest = appVersionMapper.selectOne(new LambdaQueryWrapper<AppVersion>()
                .orderByDesc(AppVersion::getId)
                .last("LIMIT 1"));
        if (latest != null && latest.getId().equals(id)) {
            throw new BusinessException("CANNOT_DELETE_LATEST", "不能删除当前最新版本");
        }
        appVersionMapper.deleteById(id);
        fileStorageService.delete(row.getFilePath());
    }

    private void broadcastUpdate(AppVersion row) {
        List<User> users = userMapper.selectList(new LambdaQueryWrapper<User>()
                .eq(User::getStatus, UserStatus.ENABLED.getCode()));
        String body = row.getNotes() == null || row.getNotes().isBlank()
                ? "桌面端有新版本，可在「检查更新」中下载安装。"
                : truncate(row.getNotes(), 200);
        JSONObject payload = new JSONObject();
        payload.set("version", row.getVersion());
        payload.set("downloadUrl", downloadUrl(row));
        payload.set("notes", row.getNotes());

        int pushed = 0;
        for (User user : users) {
            try {
                Notification notification = new Notification();
                notification.setUserId(user.getId());
                notification.setType("APP_UPDATE");
                notification.setTitle("发现新版本 v" + row.getVersion());
                notification.setBody(body);
                notification.setPayload(payload.toString());
                notification.setCreatedAt(LocalDateTime.now());
                notificationMapper.insert(notification);

                NotificationResponseDTO dto = notificationService.toDto(notification);
                // 更新通知不遵循免打扰，确保弹出更新提示
                notificationPushService.pushSystem(user.getId(), dto);
                pushed++;
            } catch (Exception e) {
                log.warn("push app update notification failed, userId={}", user.getId(), e);
            }
        }
        log.info("app version {} published, notified users={}", row.getVersion(), pushed);
    }

    private AppVersionResponseDTO toDto(AppVersion row) {
        AppVersionResponseDTO dto = new AppVersionResponseDTO();
        dto.setId(row.getId());
        dto.setVersion(row.getVersion());
        dto.setNotes(row.getNotes());
        dto.setFileName(row.getFileName());
        dto.setFileSize(row.getFileSize());
        dto.setDownloadUrl(downloadUrl(row));
        dto.setCreatedAt(row.getCreatedAt() == null ? null
                : row.getCreatedAt().format(DATE_TIME_FORMATTER));
        return dto;
    }

    private static String downloadUrl(AppVersion row) {
        return "/api/files/" + row.getFilePath();
    }

    static int compareVersions(String left, String right) {
        String[] a = left == null ? new String[0] : left.split("\\.");
        String[] b = right == null ? new String[0] : right.split("\\.");
        int length = Math.max(a.length, b.length);
        for (int i = 0; i < length; i++) {
            int x = i < a.length ? parseInt(a[i]) : 0;
            int y = i < b.length ? parseInt(b[i]) : 0;
            if (x != y) {
                return Integer.compare(x, y);
            }
        }
        return 0;
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max) + "...";
    }
}
