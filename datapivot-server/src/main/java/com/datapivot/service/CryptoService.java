package com.datapivot.service;

import com.datapivot.common.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES 加密服务（AES-256-GCM）
 * <p>用于数据库连接密码的加密存储：
 * 密文格式 Base64(12字节随机IV + 密文+16字节TAG)，同一明文每次加密结果不同。</p>
 * <p>密钥由配置项 datapivot.crypto.aes-key（支持环境变量 DP_AES_KEY 注入）经 SHA-256 派生 16 字节，
 * 因此配置任意长度口令均可安全使用。</p>
 */
@Slf4j
@Service
public class CryptoService {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKey secretKey;

    public CryptoService(@Value("${datapivot.crypto.aes-key}") String aesKey) {
        try {
            // SHA-256 派生固定 16 字节密钥，规避对口令长度的要求
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(aesKey.getBytes(StandardCharsets.UTF_8));
            this.secretKey = new SecretKeySpec(digest, 0, 16, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("AES 密钥初始化失败", e);
        }
    }

    /** 明文加密为 Base64 字符串 */
    public String encrypt(String plain) {
        if (plain == null) {
            return null;
        }
        try {
            // 每次加密使用随机 IV，保证相同明文密文不同
            byte[] iv = new byte[IV_LENGTH];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(iv.length + cipherText.length).put(iv).put(cipherText).array());
        } catch (Exception e) {
            throw new BusinessException("密码加密失败，请联系管理员检查 AES 密钥配置");
        }
    }

    /** Base64 密文解密为明文 */
    public String decrypt(String cipherText) {
        if (cipherText == null || cipherText.isBlank()) {
            return "";
        }
        try {
            byte[] all = Base64.getDecoder().decode(cipherText);
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(all, 0, iv, 0, IV_LENGTH);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] plain = cipher.doFinal(all, IV_LENGTH, all.length - IV_LENGTH);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            // 解密失败说明密钥变更或数据损坏，给出明确指引而不是抛堆栈
            throw new BusinessException("密码解密失败：可能 AES 密钥已变更，请重新编辑该连接并保存密码");
        }
    }

    /**
     * 密码脱敏：仅显示前两位 + **** + 后两位；长度不足 5 位时全部以 **** 展示
     */
    public String mask(String plain) {
        if (plain == null || plain.isEmpty()) {
            return "****";
        }
        if (plain.length() <= 4) {
            return "****";
        }
        return plain.substring(0, 2) + "****" + plain.substring(plain.length() - 2);
    }
}
