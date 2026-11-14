package com.example.javaweb.service;

import cn.hutool.json.JSONObject;
import com.example.javaweb.dto.request.SubscribePushCommandDTO;
import com.example.javaweb.dto.response.NotificationResponseDTO;
import com.example.javaweb.dto.response.PushPublicKeyResponseDTO;
import com.example.javaweb.config.WebPushProperties;
import com.example.javaweb.entity.PushSubscription;
import com.example.javaweb.exception.BusinessException;
import com.example.javaweb.mapper.PushSubscriptionMapper;
import com.example.javaweb.util.DateTimeUtil;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Subscription;
import org.apache.http.HttpResponse;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.Security;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Web Push：VAPID 初始化、订阅管理、发送。
 * 免打扰判断在 NotificationPushService 里统一做。
 */
@Service
public class WebPushService {
    private static final Logger log = LoggerFactory.getLogger(WebPushService.class);

    @Resource
    private WebPushProperties properties;

    @Resource
    private PushSubscriptionMapper subscriptionMapper;

    private PushService pushService;

    @PostConstruct
    void init() {
        if (!properties.isEnabled()) {
            log.info("web push disabled");
            return;
        }
        if (blank(properties.getVapid().getPublicKey())
                || blank(properties.getVapid().getPrivateKey())) {
            log.warn("web push enabled but VAPID keys are missing");
            return;
        }
        try {
            if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                Security.addProvider(new BouncyCastleProvider());
            }
            pushService = new PushService(
                    properties.getVapid().getPublicKey(),
                    properties.getVapid().getPrivateKey(),
                    properties.getVapid().getSubject());
            log.info("web push initialized");
        } catch (Exception e) {
            log.error("web push init failed", e);
        }
    }

    public boolean isEnabled() {
        return pushService != null;
    }

    public PushPublicKeyResponseDTO publicKey() {
        PushPublicKeyResponseDTO dto = new PushPublicKeyResponseDTO();
        dto.setEnabled(isEnabled());
        dto.setPublicKey(isEnabled() ? properties.getVapid().getPublicKey() : "");
        return dto;
    }

    public void subscribe(Long userId, SubscribePushCommandDTO command) {
        requireEnabled();
        PushSubscription existing = subscriptionMapper.selectByEndpoint(command.getEndpoint());
        String userAgent = truncate(command.getUserAgent(), 255);
        if (existing != null) {
            subscriptionMapper.updateByEndpoint(
                    command.getEndpoint(), userId,
                    command.getKeys().getP256dh(), command.getKeys().getAuth(), userAgent);
            return;
        }
        PushSubscription subscription = new PushSubscription();
        subscription.setUserId(userId);
        subscription.setEndpoint(command.getEndpoint());
        subscription.setP256dh(command.getKeys().getP256dh());
        subscription.setAuth(command.getKeys().getAuth());
        subscription.setUserAgent(userAgent);
        subscriptionMapper.insert(subscription);
    }

    public void unsubscribe(Long userId, String endpoint) {
        subscriptionMapper.deleteByEndpoint(endpoint, userId);
    }

    /**
     * 发送给用户的所有浏览器订阅。返回成功发送的数量。
     */
    public int sendToUser(Long userId, NotificationResponseDTO notification) {
        if (!isEnabled() || notification == null) {
            return 0;
        }
        List<PushSubscription> subscriptions = subscriptionMapper.selectByUser(userId);
        if (subscriptions.isEmpty()) {
            return 0;
        }
        String payload = buildPayload(notification);
        int sent = 0;
        for (PushSubscription saved : subscriptions) {
            try {
                Subscription subscription = new Subscription(
                        saved.getEndpoint(),
                        new Subscription.Keys(saved.getP256dh(), saved.getAuth()));
                Notification pushNotification = new Notification(subscription, payload);
                HttpResponse response = pushService.send(pushNotification);
                int status = response.getStatusLine().getStatusCode();
                if (status >= 200 && status < 300) {
                    subscriptionMapper.touch(saved.getId());
                    sent++;
                } else if (status == 404 || status == 410) {
                    // 订阅已失效，清理
                    subscriptionMapper.deleteById(saved.getId());
                } else {
                    log.warn("web push failed, userId={}, status={}", userId, status);
                }
            } catch (Exception e) {
                log.warn("web push send failed, userId={}, subscriptionId={}",
                        userId, saved.getId(), e);
            }
        }
        return sent;
    }

    public int sendTest(Long userId) {
        requireEnabled();
        NotificationResponseDTO dto = new NotificationResponseDTO();
        dto.setId(-1L);
        dto.setType("SYSTEM_TEST");
        dto.setTitle("测试通知");
        dto.setBody("Web Push 已配置成功，到点提醒会出现在这里。");
        dto.setRead(false);
        dto.setCreatedAt(DateTimeUtil.format(LocalDateTime.now()));
        return sendToUser(userId, dto);
    }

    private String buildPayload(NotificationResponseDTO notification) {
        JSONObject payload = new JSONObject();
        payload.set("title", notification.getTitle());
        payload.set("body", notification.getBody());
        payload.set("url", "/task");
        payload.set("tag", "notification-" + notification.getId());
        JSONObject data = new JSONObject();
        data.set("notificationId", notification.getId());
        data.set("type", notification.getType());
        data.set("payload", notification.getPayload());
        payload.set("data", data);
        return payload.toString();
    }

    private void requireEnabled() {
        if (!isEnabled()) {
            throw new BusinessException("PUSH_DISABLED", "Web Push 未启用");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
