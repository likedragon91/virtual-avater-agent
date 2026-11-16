package com.example.javaweb.service;

import com.example.javaweb.entity.Notification;
import com.example.javaweb.mapper.TaskOccurrenceMapper;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 任务状态流转与到点通知：
 * PENDING -> RINGING（到点，写入站内信 + SSE 推送）-> MISSED（超出回应窗口）。
 */
@Component
public class TaskOccurrenceScheduler {
    private static final Logger log = LoggerFactory.getLogger(TaskOccurrenceScheduler.class);

    @Resource
    private TaskOccurrenceMapper occurrenceMapper;

    @Resource
    private NotificationService notificationService;

    @Resource
    private NotificationPushService notificationPushService;

    @Scheduled(fixedDelay = 5_000, initialDelay = 3_000)
    public void advanceStatuses() {
        try {
            List<Notification> created = notificationService.scanDueAndNotify();
            for (Notification notification : created) {
                notificationPushService.push(
                        notification.getUserId(),
                        notificationService.toDto(notification));
            }

            int missed = occurrenceMapper.markMissed();
            int expired = occurrenceMapper.markExpiredPending();
            if (!created.isEmpty() || missed > 0 || expired > 0) {
                log.debug("task occurrence advanced: notified={}, missed={}, expired={}",
                        created.size(), missed, expired);
            }
        } catch (Exception e) {
            log.warn("task occurrence scheduler failed", e);
        }
    }
}
