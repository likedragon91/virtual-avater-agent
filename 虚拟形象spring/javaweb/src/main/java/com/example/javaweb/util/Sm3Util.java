package com.example.javaweb.util;

import org.bouncycastle.crypto.digests.SM3Digest;
import org.bouncycastle.util.encoders.Hex;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

public class Sm3Util {

    private static final SecureRandom RANDOM = new SecureRandom();

    /** 计算 SM3 摘要 */
    public static String sm3Hex(String input) {
        SM3Digest digest = new SM3Digest();
        byte[] bytes = input.getBytes(StandardCharsets.UTF_8);
        digest.update(bytes, 0, bytes.length);
        byte[] out = new byte[digest.getDigestSize()];
        digest.doFinal(out, 0);
        return Hex.toHexString(out);
    }

    /** 生成随机盐（16字节，32位十六进制） */
    public static String generateSalt() {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return Hex.toHexString(salt);
    }

    /**
     * 后端第二次加密：对前端密文 + 随机盐 做 SM3
     * @param frontendCipher 前端传来的 SM3 密文
     * @param salt 用户独立的随机盐
     * @return 最终存储的密码哈希
     */
    public static String backendEncrypt(String frontendCipher, String salt) {
        return sm3Hex(salt + frontendCipher);
    }

    /** 常量时间比较，避免根据比较耗时推测密码 */
    public static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }
}
