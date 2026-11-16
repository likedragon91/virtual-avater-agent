package com.example.javaweb.service;

import com.example.javaweb.dto.request.RespondTaskCommandDTO;
import com.example.javaweb.dto.response.TaskResponseResultDTO;
import com.example.javaweb.entity.RelationshipState;
import com.example.javaweb.entity.Task;
import com.example.javaweb.entity.TaskOccurrence;
import com.example.javaweb.entity.TaskResponseRecord;
import com.example.javaweb.exception.BusinessException;
import com.example.javaweb.mapper.TaskMapper;
import com.example.javaweb.mapper.TaskOccurrenceMapper;
import com.example.javaweb.mapper.TaskResponseMapper;
import com.example.javaweb.util.DateTimeUtil;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 完成度回应与算分。
 * gain = 12 × (r/100)^0.7 × 优先级 × 时效 × 连击 × 重复衰减 × 任务权重
 */
@Service
public class TaskResponseService {
    private static final BigDecimal BASE = new BigDecimal("12");
    private static final BigDecimal DAILY_CAP = new BigDecimal("40");
    private static final int MAX_DAILY_TASKS = 8;
    private static final int CORRECTION_MINUTES = 10;
    private static final int LATE_HOURS = 24;

    @Resource
    private TaskMapper taskMapper;

    @Resource
    private TaskOccurrenceMapper occurrenceMapper;

    @Resource
    private TaskResponseMapper responseMapper;

    @Resource
    private IntimacyService intimacyService;

    @Transactional
    public TaskResponseResultDTO respond(Long userId, Long occurrenceId,
                                         RespondTaskCommandDTO command) {
        TaskOccurrence occurrence = occurrenceMapper.selectById(occurrenceId);
        if (occurrence == null) {
            throw new BusinessException("NOT_FOUND", "提醒不存在");
        }
        if (!occurrence.getUserId().equals(userId)) {
            throw new BusinessException("FORBIDDEN", "没有权限回应此提醒");
        }
        Task task = taskMapper.selectById(occurrence.getTaskId());
        if (task == null || !task.getUserId().equals(userId)) {
            throw new BusinessException("NOT_FOUND", "任务不存在");
        }

        // 同一幂等键重复提交：直接返回已有结果
        TaskResponseRecord byKey = responseMapper.selectByIdempotencyKey(command.getIdempotencyKey());
        if (byKey != null) {
            if (!Objects.equals(byKey.getOccurrenceId(), occurrenceId)) {
                throw new BusinessException("PARAM_ERROR", "idempotencyKey 已被其他提醒使用");
            }
            return buildResult(byKey, task, false);
        }

        TaskResponseRecord existing = responseMapper.selectByOccurrence(occurrenceId);
        if (existing != null) {
            return correct(userId, task, occurrence, existing, command);
        }

        return submit(userId, task, occurrence, command);
    }

    private TaskResponseResultDTO submit(Long userId, Task task, TaskOccurrence occurrence,
                                         RespondTaskCommandDTO command) {
        LocalDateTime now = LocalDateTime.now();
        validateTimeWindow(occurrence, now);
        if ("CANCELED".equals(occurrence.getStatus())) {
            throw new BusinessException("CANCELED", "该提醒已取消");
        }
        if ("RESPONDED".equals(occurrence.getStatus())) {
            throw new BusinessException("ALREADY_RESPONDED", "该提醒已回应");
        }

        boolean late = now.isAfter(occurrence.getExpiresAt());
        BigDecimal timeliness = late ? new BigDecimal("0.30") : new BigDecimal("1.00");
        long previousResponses = responseMapper.countByTaskId(task.getId());
        BigDecimal raw = calculateRaw(userId, task, command.getCompletionRatio(),
                timeliness, previousResponses);

        LocalDate today = LocalDate.now();
        BigDecimal gained = safe(responseMapper.sumDailyDelta(userId, today));
        long count = responseMapper.countDailyResponses(userId, today);
        BigDecimal delta = applyDailyCap(raw, gained, count, false);

        TaskResponseRecord response = new TaskResponseRecord();
        response.setUserId(userId);
        response.setTaskId(task.getId());
        response.setOccurrenceId(occurrence.getId());
        response.setCompletionRatio(command.getCompletionRatio());
        response.setNote(Boolean.FALSE.equals(task.getAllowNote())
                ? null : trimToNull(command.getNote()));
        response.setRespondedAt(now);
        response.setLatencySec(latencySeconds(occurrence.getOccurAt(), now));
        response.setTimeliness(timeliness);
        response.setIntimacyDelta(delta);
        response.setIdempotencyKey(command.getIdempotencyKey());
        response.setCreatedAt(now);

        try {
            responseMapper.insert(response);
        } catch (DuplicateKeyException e) {
            TaskResponseRecord raced = responseMapper.selectByOccurrence(occurrence.getId());
            if (raced != null) {
                return buildResult(raced, task, false);
            }
            throw new BusinessException("CONFLICT", "提交冲突，请刷新后重试");
        }

        occurrence.setStatus("RESPONDED");
        occurrence.setRespondedAt(now);
        occurrenceMapper.updateById(occurrence);

        intimacyService.applyGain(userId, delta, "TASK_RESPONSE", "TASK_RESPONSE", response.getId());
        intimacyService.refreshDaily(userId);
        return buildResult(response, task, false);
    }

    private TaskResponseResultDTO correct(Long userId, Task task, TaskOccurrence occurrence,
                                          TaskResponseRecord existing,
                                          RespondTaskCommandDTO command) {
        LocalDateTime now = LocalDateTime.now();
        if (!existing.getUserId().equals(userId)) {
            throw new BusinessException("FORBIDDEN", "没有权限修正此回应");
        }
        if (existing.getRevisedAt() != null) {
            throw new BusinessException("ALREADY_REVISED", "已经修正过，不能再次修改");
        }
        if (existing.getRespondedAt() == null
                || now.isAfter(existing.getRespondedAt().plusMinutes(CORRECTION_MINUTES))) {
            throw new BusinessException("CORRECTION_EXPIRED", "超过 10 分钟，不能再修正");
        }

        // 修正时排除自身，避免重复衰减计算
        long previousResponses = Math.max(responseMapper.countByTaskId(task.getId()) - 1, 0);
        BigDecimal raw = calculateRaw(userId, task, command.getCompletionRatio(),
                existing.getTimeliness(), previousResponses);

        LocalDate today = LocalDate.now();
        BigDecimal gained = safe(responseMapper.sumDailyDelta(userId, today));
        BigDecimal original = existing.getIntimacyDelta() == null
                ? BigDecimal.ZERO : existing.getIntimacyDelta();
        BigDecimal gainedExcludingThis = gained.subtract(original);
        long countExcludingThis = Math.max(responseMapper.countDailyResponses(userId, today) - 1, 0);
        BigDecimal newDelta = applyDailyCap(raw, gainedExcludingThis, countExcludingThis, true);
        BigDecimal difference = newDelta.subtract(original);

        existing.setCompletionRatio(command.getCompletionRatio());
        existing.setNote(Boolean.FALSE.equals(task.getAllowNote())
                ? "" : (trimToNull(command.getNote()) == null ? "" : command.getNote().trim()));
        existing.setIntimacyDelta(newDelta);
        existing.setRevisedAt(now);
        responseMapper.updateById(existing);

        intimacyService.applyAdjustment(userId, difference, "TASK_RESPONSE", existing.getId());
        intimacyService.refreshDaily(userId);
        return buildResult(existing, task, true);
    }

    private BigDecimal calculateRaw(Long userId, Task task, int completionRatio,
                                    BigDecimal timeliness, long previousResponses) {
        double ratio = Math.max(0, Math.min(completionRatio, 100)) / 100.0;
        double growth = Math.pow(ratio, 0.7);
        double priorityWeight = switch (task.getPriority() == null ? 2 : task.getPriority()) {
            case 1 -> 0.8;
            case 3 -> 1.3;
            default -> 1.0;
        };
        int streakDays = intimacyService.computeStreakForScoring(userId);
        double streakFactor = 1 + Math.min(streakDays, 10) * 0.02;
        double noveltyFactor = 1.0 / (1 + 0.1 * Math.max(previousResponses, 0));
        double taskWeight = task.getIntimacyWeight() == null
                ? 1.0 : task.getIntimacyWeight().doubleValue();
        double timelinessFactor = timeliness == null ? 1.0 : timeliness.doubleValue();

        double raw = BASE.doubleValue() * growth * priorityWeight * timelinessFactor
                * streakFactor * noveltyFactor * taskWeight;
        return BigDecimal.valueOf(raw).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal applyDailyCap(BigDecimal raw, BigDecimal gained, long count,
                                     boolean correction) {
        if (!correction && count >= MAX_DAILY_TASKS) {
            return BigDecimal.ZERO;
        }
        BigDecimal remaining = DAILY_CAP.subtract(gained);
        if (remaining.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal delta = raw.min(remaining);
        return delta.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    private TaskResponseResultDTO buildResult(TaskResponseRecord response, Task task,
                                              boolean corrected) {
        Long userId = response.getUserId();
        RelationshipState state = intimacyService.ensureState(userId);
        LocalDate today = LocalDate.now();
        BigDecimal gained = safe(responseMapper.sumDailyDelta(userId, today));
        long count = responseMapper.countDailyResponses(userId, today);
        String stageCode = state.getStageCode() == null ? "S0" : state.getStageCode();

        TaskResponseResultDTO dto = new TaskResponseResultDTO();
        dto.setResponseId(response.getId());
        dto.setTaskId(task.getId());
        dto.setOccurrenceId(response.getOccurrenceId());
        dto.setCompletionRatio(response.getCompletionRatio());
        dto.setIntimacyDelta(response.getIntimacyDelta());
        dto.setIntimacyTotal(state.getIntimacyTotal());
        dto.setStageCode(stageCode);
        dto.setStageName(IntimacyService.stageName(stageCode));
        dto.setDailyGained(gained);
        dto.setDailyCap(DAILY_CAP.intValue());
        dto.setTaskCountToday(count);
        dto.setMaxDailyTasks(MAX_DAILY_TASKS);
        dto.setFeedback(feedback(response.getCompletionRatio()));
        dto.setCorrected(corrected);
        dto.setRespondedAt(DateTimeUtil.format(response.getRespondedAt()));
        return dto;
    }

    private static void validateTimeWindow(TaskOccurrence occurrence, LocalDateTime now) {
        if (occurrence.getOccurAt() != null && now.isBefore(occurrence.getOccurAt())) {
            throw new BusinessException("NOT_DUE", "还没到回应时间");
        }
        if (occurrence.getExpiresAt() != null
                && now.isAfter(occurrence.getExpiresAt().plusHours(LATE_HOURS))) {
            throw new BusinessException("TOO_LATE", "已超过 24 小时补评时间");
        }
    }

    private static Integer latencySeconds(LocalDateTime occurAt, LocalDateTime now) {
        if (occurAt == null) {
            return null;
        }
        long seconds = Duration.between(occurAt, now).getSeconds();
        return (int) Math.max(0, Math.min(seconds, Integer.MAX_VALUE));
    }

    private static String feedback(Integer ratio) {
        if (ratio == null) {
            return "谢谢你的回应。";
        }
        if (ratio <= 0) {
            return "今天先休息一下，没关系。";
        }
        if (ratio <= 30) {
            return "动了一点，也是开始。";
        }
        if (ratio <= 60) {
            return "已经做了一半，继续保持。";
        }
        if (ratio <= 90) {
            return "就差一点了。";
        }
        return "完成得很好，辛苦了。";
    }

    private static BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
