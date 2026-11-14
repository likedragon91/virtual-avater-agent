package com.example.javaweb.service;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.javaweb.dto.response.NotificationListResponseDTO;
import com.example.javaweb.dto.response.NotificationResponseDTO;
import com.example.javaweb.entity.Notification;
import com.example.javaweb.entity.Task;
import com.example.javaweb.entity.TaskOccurrence;
import com.example.javaweb.mapper.NotificationMapper;
import com.example.javaweb.mapper.TaskMapper;
import com.example.javaweb.mapper.TaskOccurrenceMapper;
import com.example.javaweb.util.DateTimeUtil;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 站内通知：到点扫描、列表、未读数、已读。
 */
@Service
public class NotificationService {

    @Resource
    private NotificationMapper notificationMapper;

    @Resource
    private TaskOccurrenceMapper occurrenceMapper;

    @Resource
    private TaskMapper taskMapper;

    /**
     * 扫描到点且仍在回应窗口内的 PENDING occurrence：
     * 条件更新为 RINGING，成功后写入站内信。
     * 条件更新保证多实例/多次调度只生成一条通知。
     */
    @Transactional(rollbackFor = Exception.class)
    public List<Notification> scanDueAndNotify() {
        List<TaskOccurrence> due = occurrenceMapper.selectDuePending(100);
        List<Notification> created = new ArrayList<>();
        for (TaskOccurrence occurrence : due) {
            if (occurrenceMapper.markRingingById(occurrence.getId()) != 1) {
                continue;
            }
            Task task = taskMapper.selectById(occurrence.getTaskId());
            if (task == null) {
                continue;
            }
            Notification notification = build(occurrence, task);
            notificationMapper.insert(notification);
            created.add(notification);
        }
        return created;
    }

    public NotificationListResponseDTO list(Long userId, long page, long size) {
        long currentPage = Math.max(page, 1);
        long pageSize = Math.min(Math.max(size, 1), 50);
        long offset = (currentPage - 1) * pageSize;

        Long total = notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId));

        List<Notification> rows = notificationMapper.selectList(
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .orderByDesc(Notification::getId)
                        .last("LIMIT " + pageSize + " OFFSET " + offset));

        NotificationListResponseDTO result = new NotificationListResponseDTO();
        List<NotificationResponseDTO> records = new ArrayList<>();
        for (Notification row : rows) {
            records.add(toDto(row));
        }
        result.setRecords(records);
        result.setTotal(total == null ? 0 : total);
        result.setPage(currentPage);
        result.setSize(pageSize);
        result.setUnreadCount(unreadCount(userId));
        return result;
    }

    public long unreadCount(Long userId) {
        Long count = notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .isNull(Notification::getReadAt));
        return count == null ? 0 : count;
    }

    public void markRead(Long userId, Long notificationId) {
        notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getId, notificationId)
                .eq(Notification::getUserId, userId)
                .isNull(Notification::getReadAt)
                .set(Notification::getReadAt, LocalDateTime.now()));
    }

    public void markAllRead(Long userId) {
        notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .isNull(Notification::getReadAt)
                .set(Notification::getReadAt, LocalDateTime.now()));
    }

    public NotificationResponseDTO toDto(Notification notification) {
        NotificationResponseDTO dto = new NotificationResponseDTO();
        dto.setId(notification.getId());
        dto.setType(notification.getType());
        dto.setTitle(notification.getTitle());
        dto.setBody(notification.getBody());
        dto.setRead(notification.getReadAt() != null);
        dto.setCreatedAt(DateTimeUtil.format(notification.getCreatedAt()));

        Object payload = null;
        if (notification.getPayload() != null && !notification.getPayload().isBlank()) {
            try {
                payload = JSONUtil.parseObj(notification.getPayload());
            } catch (Exception e) {
                payload = notification.getPayload();
            }
        }
        dto.setPayload(payload);
        return dto;
    }

    private static Notification build(TaskOccurrence occurrence, Task task) {
        Notification notification = new Notification();
        notification.setUserId(task.getUserId());
        notification.setType("TASK_REMINDER");
        notification.setTitle("该做「" + truncate(task.getTitle(), 80) + "」了");
        notification.setBody("现在可以花一分钟回应一下；0% 也算一次认真回应。");

        JSONObject payload = new JSONObject();
        payload.set("occurrenceId", occurrence.getId());
        payload.set("taskId", task.getId());
        payload.set("taskTitle", task.getTitle());
        payload.set("occurAt", DateTimeUtil.format(occurrence.getOccurAt()));
        payload.set("expiresAt", DateTimeUtil.format(occurrence.getExpiresAt()));
        notification.setPayload(payload.toString());
        notification.setCreatedAt(LocalDateTime.now());
        return notification;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max) + "...";
    }
}
