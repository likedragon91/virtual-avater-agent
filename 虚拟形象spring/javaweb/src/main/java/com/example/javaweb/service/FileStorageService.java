package com.example.javaweb.service;

import com.example.javaweb.config.UploadProperties;
import com.example.javaweb.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 本地文件存储：头像图片与 Live2D zip。
 *
 * 文件名由 UUID 生成，原始文件名只用于取扩展名和展示；
 * 所有路径都规范化并校验必须位于上传根目录内，防止目录穿越。
 */
@Service
public class FileStorageService {
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "webp", "gif");
    private static final Set<String> ZIP_EXTENSIONS = Set.of("zip");
    private static final Set<String> INSTALLER_EXTENSIONS = Set.of("exe");

    @Resource
    private UploadProperties properties;

    private Path root;

    @PostConstruct
    void init() {
        root = Paths.get(properties.getDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建上传目录: " + root, e);
        }
    }

    public StoredFile storeImage(MultipartFile file, String subdir) {
        return store(file, subdir, IMAGE_EXTENSIONS, properties.getMaxImageBytes(), Kind.IMAGE);
    }

    public StoredFile storeZip(MultipartFile file, String subdir) {
        return store(file, subdir, ZIP_EXTENSIONS, properties.getMaxModelBytes(), Kind.ZIP);
    }

    /** Windows 桌面端 NSIS 安装包 */
    public StoredFile storeInstaller(MultipartFile file) {
        return store(file, "updates", INSTALLER_EXTENSIONS,
                properties.getMaxInstallerBytes(), Kind.INSTALLER);
    }

    public org.springframework.core.io.Resource load(String relativePath) {
        Path path = resolve(relativePath);
        if (!Files.isRegularFile(path)) {
            throw new BusinessException("NOT_FOUND", "文件不存在");
        }
        return new FileSystemResource(path);
    }

    public void delete(String relativePath) {
        try {
            Files.deleteIfExists(resolve(relativePath));
        } catch (Exception ignored) {
            // 文件已不存在时不阻塞删除记录
        }
    }

    public Path resolve(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new BusinessException("NOT_FOUND", "文件不存在");
        }
        Path path = root.resolve(relativePath).normalize();
        if (!path.startsWith(root)) {
            throw new BusinessException("FORBIDDEN", "非法的文件路径");
        }
        return path;
    }

    private StoredFile store(MultipartFile file, String subdir, Set<String> allowedExtensions,
                             long maxBytes, Kind kind) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("PARAM_ERROR", "请选择要上传的文件");
        }
        if (file.getSize() > maxBytes) {
            throw new BusinessException("FILE_TOO_LARGE",
                    "文件不能超过 " + (maxBytes / 1024 / 1024) + "MB");
        }

        String originalName = file.getOriginalFilename() == null
                ? "upload" : Paths.get(file.getOriginalFilename()).getFileName().toString();
        String extension = extensionOf(originalName);
        if (!allowedExtensions.contains(extension)) {
            throw new BusinessException("PARAM_ERROR", "不支持的文件类型");
        }

        validateMagicBytes(file, kind);

        String filename = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        Path directory = root.resolve(subdir).normalize();
        if (!directory.startsWith(root)) {
            throw new BusinessException("FORBIDDEN", "非法的上传目录");
        }
        Path target = directory.resolve(filename).normalize();
        try {
            Files.createDirectories(directory);
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new BusinessException("UPLOAD_FAILED", "文件保存失败，请稍后重试");
        }

        String relativePath = subdir.replace('\\', '/') + "/" + filename;
        String url = trimTrailingSlash(properties.getPublicPath()) + "/" + relativePath;
        return new StoredFile(relativePath, url, file.getSize(), originalName);
    }

    private static String extensionOf(String filename) {
        int index = filename.lastIndexOf('.');
        if (index < 0 || index == filename.length() - 1) {
            return "";
        }
        return filename.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private static void validateMagicBytes(MultipartFile file, Kind kind) {
        byte[] header = new byte[12];
        int read;
        try (InputStream input = file.getInputStream()) {
            read = input.read(header);
        } catch (IOException e) {
            throw new BusinessException("UPLOAD_FAILED", "无法读取上传文件");
        }
        if (read < 4) {
            throw new BusinessException("PARAM_ERROR", "文件内容不完整");
        }
        boolean valid = switch (kind) {
            case ZIP -> isZip(header);
            case IMAGE -> isImage(header);
            case INSTALLER -> isExecutable(header);
        };
        if (!valid) {
            throw new BusinessException("PARAM_ERROR", "文件内容与格式不符");
        }
    }

    private static boolean isExecutable(byte[] header) {
        // Windows PE：MZ
        return header[0] == 0x4D && header[1] == 0x5A;
    }

    private static boolean isZip(byte[] header) {
        return header[0] == 0x50 && header[1] == 0x4B
                && (header[2] == 0x03 || header[2] == 0x05 || header[2] == 0x07);
    }

    private static boolean isImage(byte[] header) {
        // PNG
        if ((header[0] & 0xFF) == 0x89 && header[1] == 0x50 && header[2] == 0x4E && header[3] == 0x47) {
            return true;
        }
        // JPEG
        if ((header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8) {
            return true;
        }
        // GIF
        if (header[0] == 0x47 && header[1] == 0x49 && header[2] == 0x46) {
            return true;
        }
        // WEBP: RIFF....WEBP
        return header[0] == 0x52 && header[1] == 0x49 && header[2] == 0x46 && header[3] == 0x46
                && header[8] == 0x57 && header[9] == 0x45 && header[10] == 0x42 && header[11] == 0x50;
    }

    private static String trimTrailingSlash(String value) {
        String result = value == null ? "" : value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private enum Kind {
        IMAGE, ZIP, INSTALLER
    }

    public record StoredFile(String relativePath, String url, long size, String originalName) {
    }
}
