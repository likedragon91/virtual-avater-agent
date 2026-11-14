package com.example.javaweb.service;

import cn.hutool.json.JSONUtil;
import com.example.javaweb.dto.response.NotificationResponseDTO;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 按用户维护通知 SSE 连接。一个用户可以有多个页面/标签页。
 */
@Service
public class NotificationSseService {
    private static final Logger log = LoggerFactory.getLogger(NotificationSseService.class);

    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long userId) {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.computeIfAbsent(userId, key -> new CopyOnWriteArrayList<>()).add(emitter);

        Runnable cleanup = () -> remove(userId, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(error -> cleanup.run());

        try {
            send(emitter, "connected", "{\"ok\":true}");
        } catch (IOException e) {
            cleanup.run();
        }
        return emitter;
    }

    public void push(Long userId, NotificationResponseDTO notification) {
        List<SseEmitter> list = emitters.get(userId);
        if (list == null || list.isEmpty()) {
            return;
        }
        String json = JSONUtil.toJsonStr(notification);
        for (SseEmitter emitter : list) {
            try {
                send(emitter, "notification", json);
            } catch (Exception e) {
                remove(userId, emitter);
            }
        }
    }

    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    public void heartbeat() {
        for (Map.Entry<Long, CopyOnWriteArrayList<SseEmitter>> entry : emitters.entrySet()) {
            for (SseEmitter emitter : entry.getValue()) {
                try {
                    send(emitter, "ping", "{\"ts\":" + System.currentTimeMillis() + "}");
                } catch (Exception e) {
                    remove(entry.getKey(), emitter);
                }
            }
        }
    }

    private void send(SseEmitter emitter, String event, String json) throws IOException {
        emitter.send(SseEmitter.event()
                .name(event)
                .data(json, MediaType.APPLICATION_JSON));
    }

    private void remove(Long userId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> list = emitters.get(userId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                emitters.remove(userId, list);
            }
        }
    }
}
