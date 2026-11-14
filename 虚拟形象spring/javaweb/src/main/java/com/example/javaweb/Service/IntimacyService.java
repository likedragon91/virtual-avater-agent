package com.example.javaweb.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.javaweb.dto.response.IntimacyLogListResponseDTO;
import com.example.javaweb.dto.response.IntimacyLogResponseDTO;
import com.example.javaweb.dto.response.RelationshipResponseDTO;
import com.example.javaweb.entity.IntimacyLog;
import com.example.javaweb.entity.RelationshipState;
import com.example.javaweb.entity.StageEvent;
import com.example.javaweb.mapper.DailyIntimacyMapper;
import com.example.javaweb.mapper.IntimacyLogMapper;
import com.example.javaweb.mapper.RelationshipStateMapper;
import com.example.javaweb.mapper.StageEventMapper;
import com.example.javaweb.mapper.TaskResponseMapper;
import com.example.javaweb.util.DateTimeUtil;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 亲密度、阶段与每日额度。
 * 阶段阈值：S0 0 / S1 150 / S2 400 / S3 700 / S4 1000。
 */
@Service
public class IntimacyService {
    public static final BigDecimal DAILY_CAP = new BigDecimal("40");
    public static final int MAX_DAILY_TASKS = 8;
    public static final BigDecimal MAX_INTIMACY = new BigDecimal("1000");

    @Resource
    private RelationshipStateMapper relationshipMapper;

    @Resource
    private IntimacyLogMapper intimacyLogMapper;

    @Resource
    private StageEventMapper stageEventMapper;

    @Resource
    private TaskResponseMapper taskResponseMapper;

    @Resource
    private DailyIntimacyMapper dailyIntimacyMapper;

    public RelationshipState ensureState(Long userId) {
        RelationshipState state = relationshipMapper.selectById(userId);
        if (state != null) {
            return state;
        }
        state = new RelationshipState();
        state.setUserId(userId);
        state.setIntimacyTotal(BigDecimal.ZERO);
        state.setStageCode("S0");
        state.setStageSince(LocalDateTime.now());
        state.setUpdatedAt(LocalDateTime.now());
        try {
            relationshipMapper.insert(state);
            return state;
        } catch (DuplicateKeyException e) {
            return relationshipMapper.selectById(userId);
        }
    }

    public RelationshipResponseDTO getRelationship(Long userId) {
        RelationshipState state = ensureState(userId);
        LocalDate today = LocalDate.now();
        BigDecimal gained = safe(taskResponseMapper.sumDailyDelta(userId, today));
        long taskCount = taskResponseMapper.countDailyResponses(userId, today);
        int streak = computeStreak(userId);

        BigDecimal total = state.getIntimacyTotal() == null ? BigDecimal.ZERO : state.getIntimacyTotal();
        String stageCode = state.getStageCode() == null ? "S0" : state.getStageCode();
        String nextStage = nextStage(stageCode);

        RelationshipResponseDTO dto = new RelationshipResponseDTO();
        dto.setIntimacyTotal(total);
        dto.setStageCode(stageCode);
        dto.setStageName(stageName(stageCode));
        dto.setStageSince(DateTimeUtil.format(state.getStageSince()));
        dto.setNextStageCode(nextStage);
        dto.setNextStageName(nextStage == null ? null : stageName(nextStage));
        dto.setNextThreshold(nextStage == null ? null : thresholdFor(nextStage));
        dto.setProgressPercent(total.multiply(new BigDecimal("100"))
                .divide(MAX_INTIMACY, 1, RoundingMode.HALF_UP)
                .min(new BigDecimal("100"))
                .doubleValue());
        dto.setTodayGained(gained);
        dto.setDailyCap(DAILY_CAP.intValue());
        dto.setTaskCountToday(taskCount);
        dto.setMaxDailyTasks(MAX_DAILY_TASKS);
        dto.setStreakDays(streak);
        dto.setUserCapStage(state.getUserCapStage());
        return dto;
    }

    public IntimacyLogListResponseDTO getLogs(Long userId, long page, long size) {
        long currentPage = Math.max(page, 1);
        long pageSize = Math.min(Math.max(size, 1), 50);
        long offset = (currentPage - 1) * pageSize;

        LambdaQueryWrapper<IntimacyLog> countQuery = new LambdaQueryWrapper<IntimacyLog>()
                .eq(IntimacyLog::getUserId, userId);
        Long total = intimacyLogMapper.selectCount(countQuery);

        List<IntimacyLog> rows = intimacyLogMapper.selectList(
                new LambdaQueryWrapper<IntimacyLog>()
                        .eq(IntimacyLog::getUserId, userId)
                        .orderByDesc(IntimacyLog::getId)
                        .last("LIMIT " + pageSize + " OFFSET " + offset));

        IntimacyLogListResponseDTO result = new IntimacyLogListResponseDTO();
        result.setRecords(rows.stream().map(this::toLogDTO).collect(Collectors.toList()));
        result.setTotal(total == null ? 0 : total);
        result.setPage(currentPage);
        result.setSize(pageSize);
        return result;
    }

    /**
     * 首次回应加分。返回加分后的关系状态。
     */
    public RelationshipState applyGain(Long userId, BigDecimal delta, String reason,
                                       String refType, Long refId) {
        RelationshipState state = ensureState(userId);
        BigDecimal current = state.getIntimacyTotal() == null
                ? BigDecimal.ZERO : state.getIntimacyTotal();
        BigDecimal newTotal = clamp(current.add(delta));
        String oldStage = state.getStageCode() == null ? "S0" : state.getStageCode();
        String newStage = stageFor(newTotal);

        state.setIntimacyTotal(newTotal);
        state.setUpdatedAt(LocalDateTime.now());
        boolean levelUp = stageIndex(newStage) > stageIndex(oldStage);
        if (levelUp) {
            state.setStageCode(newStage);
            state.setStageSince(LocalDateTime.now());
        }
        relationshipMapper.updateById(state);

        insertLog(userId, delta, newTotal, reason, refType, refId);
        if (levelUp) {
            recordStageEvent(userId, oldStage, newStage, newTotal);
        }
        return state;
    }

    /**
     * 10 分钟内修正。difference 可以为负；阶段只升不降。
     */
    public RelationshipState applyAdjustment(Long userId, BigDecimal difference,
                                             String refType, Long refId) {
        RelationshipState state = ensureState(userId);
        BigDecimal current = state.getIntimacyTotal() == null
                ? BigDecimal.ZERO : state.getIntimacyTotal();
        BigDecimal newTotal = clamp(current.add(difference));
        String oldStage = state.getStageCode() == null ? "S0" : state.getStageCode();
        String candidate = stageFor(newTotal);
        String newStage = stageIndex(candidate) > stageIndex(oldStage) ? candidate : oldStage;

        state.setIntimacyTotal(newTotal);
        state.setStageCode(newStage);
        state.setUpdatedAt(LocalDateTime.now());
        if (!newStage.equals(oldStage)) {
            state.setStageSince(LocalDateTime.now());
        }
        relationshipMapper.updateById(state);

        insertLog(userId, difference, newTotal, "ADJUST", refType, refId);
        if (!newStage.equals(oldStage)) {
            recordStageEvent(userId, oldStage, newStage, newTotal);
        }
        return state;
    }

    /**
     * 回写 daily_intimacy（按 task_response 当日数据重新聚合）。
     */
    public void refreshDaily(Long userId) {
        LocalDate today = LocalDate.now();
        BigDecimal gained = safe(taskResponseMapper.sumDailyDelta(userId, today));
        long count = taskResponseMapper.countDailyResponses(userId, today);
        BigDecimal avg = safe(taskResponseMapper.avgDailyCompletion(userId, today));
        int streak = computeStreak(userId);
        dailyIntimacyMapper.upsert(userId, today, gained, (int) count, avg, streak);
    }

    /** 展示用连续天数：必须包含今天，否则为 0。 */
    public int computeStreak(Long userId) {
        Set<LocalDate> dates = responseDates(userId);
        LocalDate cursor = LocalDate.now();
        if (!dates.contains(cursor)) {
            return 0;
        }
        int streak = 0;
        while (dates.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    /** 算分用连续天数：把本次即将提交的回应算进去。 */
    public int computeStreakForScoring(Long userId) {
        Set<LocalDate> dates = responseDates(userId);
        LocalDate cursor = LocalDate.now().minusDays(1);
        int streak = 1;
        while (dates.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    public String stageFor(BigDecimal total) {
        if (total == null) {
            return "S0";
        }
        if (total.compareTo(new BigDecimal("1000")) >= 0) {
            return "S4";
        }
        if (total.compareTo(new BigDecimal("700")) >= 0) {
            return "S3";
        }
        if (total.compareTo(new BigDecimal("400")) >= 0) {
            return "S2";
        }
        if (total.compareTo(new BigDecimal("150")) >= 0) {
            return "S1";
        }
        return "S0";
    }

    public static String stageName(String code) {
        if (code == null) {
            return "初识";
        }
        return switch (code) {
            case "S1" -> "熟悉";
            case "S2" -> "亲近";
            case "S3" -> "信赖";
            case "S4" -> "羁绊";
            default -> "初识";
        };
    }

    private static int stageIndex(String code) {
        if (code == null) {
            return 0;
        }
        return switch (code) {
            case "S1" -> 1;
            case "S2" -> 2;
            case "S3" -> 3;
            case "S4" -> 4;
            default -> 0;
        };
    }

    private static String nextStage(String code) {
        return switch (code == null ? "S0" : code) {
            case "S0" -> "S1";
            case "S1" -> "S2";
            case "S2" -> "S3";
            case "S3" -> "S4";
            default -> null;
        };
    }

    private static BigDecimal thresholdFor(String code) {
        return switch (code) {
            case "S1" -> new BigDecimal("150");
            case "S2" -> new BigDecimal("400");
            case "S3" -> new BigDecimal("700");
            case "S4" -> new BigDecimal("1000");
            default -> BigDecimal.ZERO;
        };
    }

    private Set<LocalDate> responseDates(Long userId) {
        List<LocalDate> dates = taskResponseMapper.selectResponseDates(
                userId, LocalDateTime.now().minusDays(90));
        return new HashSet<>(dates == null ? List.of() : dates);
    }

    private void recordStageEvent(Long userId, String from, String to, BigDecimal balance) {
        StageEvent event = new StageEvent();
        event.setUserId(userId);
        event.setFromStage(from);
        event.setToStage(to);
        event.setCreatedAt(LocalDateTime.now());
        try {
            stageEventMapper.insert(event);
        } catch (DuplicateKeyException ignored) {
            // 阶段事件唯一键防重复
        }
        insertLog(userId, BigDecimal.ZERO, balance, "LEVEL_UP", "STAGE", event.getId());
    }

    private void insertLog(Long userId, BigDecimal delta, BigDecimal balance,
                           String reason, String refType, Long refId) {
        IntimacyLog log = new IntimacyLog();
        log.setUserId(userId);
        log.setDelta(delta == null ? BigDecimal.ZERO : delta);
        log.setBalanceAfter(balance);
        log.setReason(reason);
        log.setRefType(refType);
        log.setRefId(refId);
        log.setCreatedAt(LocalDateTime.now());
        intimacyLogMapper.insert(log);
    }

    private IntimacyLogResponseDTO toLogDTO(IntimacyLog log) {
        IntimacyLogResponseDTO dto = new IntimacyLogResponseDTO();
        dto.setId(log.getId());
        dto.setDelta(log.getDelta());
        dto.setBalanceAfter(log.getBalanceAfter());
        dto.setReason(log.getReason());
        dto.setReasonLabel(reasonLabel(log.getReason()));
        dto.setRefType(log.getRefType());
        dto.setRefId(log.getRefId());
        dto.setCreatedAt(DateTimeUtil.format(log.getCreatedAt()));
        return dto;
    }

    private static String reasonLabel(String reason) {
        if (reason == null) {
            return "-";
        }
        return switch (reason) {
            case "TASK_RESPONSE" -> "任务回应";
            case "ADJUST" -> "修正调整";
            case "LEVEL_UP" -> "阶段升级";
            case "DAILY_BONUS" -> "每日加成";
            default -> reason;
        };
    }

    private static BigDecimal clamp(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        return value.min(MAX_INTIMACY);
    }

    private static BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
