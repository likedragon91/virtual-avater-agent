package com.example.javaweb.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.javaweb.dto.request.CreateTaskCommandDTO;
import com.example.javaweb.dto.request.UpdateTaskCommandDTO;
import com.example.javaweb.dto.response.OccurrenceItemResponseDTO;
import com.example.javaweb.dto.response.TaskItemResponseDTO;
import com.example.javaweb.dto.response.TaskListResponseDTO;
import com.example.javaweb.dto.projection.OccurrenceRow;
import com.example.javaweb.entity.Task;
import com.example.javaweb.entity.TaskOccurrence;
import com.example.javaweb.exception.BusinessException;
import com.example.javaweb.mapper.TaskMapper;
import com.example.javaweb.mapper.TaskOccurrenceMapper;
import com.example.javaweb.util.DateTimeUtil;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 事务闹钟：CRUD、基础重复规则、未来 30 天 occurrence 生成。
 */
@Service
public class TaskService {
    private static final Set<String> TASK_STATUSES = Set.of("ACTIVE", "PAUSED", "ARCHIVED");
    private static final Set<String> OCCURRENCE_STATUSES =
            Set.of("PENDING", "RINGING", "RESPONDED", "MISSED", "CANCELED");
    private static final int MAX_OCCURRENCES = 200;

    @Resource
    private TaskMapper taskMapper;

    @Resource
    private TaskOccurrenceMapper occurrenceMapper;

    @Transactional
    public TaskItemResponseDTO create(Long userId, CreateTaskCommandDTO command) {
        LocalDateTime dueAt = DateTimeUtil.parseFlexible(command.getDueAt());
        if (dueAt == null) {
            throw new BusinessException("PARAM_ERROR", "截止时间不能为空");
        }
        int priority = command.getPriority() == null ? 2 : command.getPriority();
        int window = command.getResponseWindowMinutes() == null ? 120 : command.getResponseWindowMinutes();
        validatePriority(priority);
        validateWindow(window);

        Task task = new Task();
        task.setUserId(userId);
        task.setTitle(command.getTitle().trim());
        task.setNote(trimToNull(command.getNote()));
        task.setDueAt(dueAt);
        task.setTz(hasText(command.getTz()) ? command.getTz().trim() : "Asia/Shanghai");
        task.setRrule(normalizeRrule(command.getRrule()));
        task.setPriority(priority);
        task.setStatus("ACTIVE");
        task.setSource("MANUAL");
        task.setResponseWindowMinutes(window);
        task.setAllowNote(command.getAllowNote() == null ? Boolean.TRUE : command.getAllowNote());
        task.setIntimacyWeight(BigDecimal.ONE);
        task.setRemindOffsets("[0]");
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.insert(task);

        generateOccurrences(task);
        return toTaskDTO(task);
    }

    public TaskListResponseDTO list(Long userId, long page, long size, String status) {
        long currentPage = Math.max(page, 1);
        long pageSize = Math.min(Math.max(size, 1), 50);
        long offset = (currentPage - 1) * pageSize;
        String normalizedStatus = normalizeTaskStatus(status);

        LambdaQueryWrapper<Task> countQuery = new LambdaQueryWrapper<Task>()
                .eq(Task::getUserId, userId);
        if (normalizedStatus != null) {
            countQuery.eq(Task::getStatus, normalizedStatus);
        }
        Long total = taskMapper.selectCount(countQuery);

        List<Task> rows = taskMapper.selectTaskList(userId, normalizedStatus, offset, pageSize);
        TaskListResponseDTO result = new TaskListResponseDTO();
        result.setRecords(rows.stream().map(this::toTaskDTO).toList());
        result.setTotal(total == null ? 0 : total);
        result.setPage(currentPage);
        result.setSize(pageSize);
        return result;
    }

    public TaskItemResponseDTO get(Long userId, Long taskId) {
        Task task = requireOwned(userId, taskId);
        fillNextOccurrence(task);
        return toTaskDTO(task);
    }

    @Transactional
    public TaskItemResponseDTO update(Long userId, Long taskId, UpdateTaskCommandDTO command) {
        Task task = requireOwned(userId, taskId);
        boolean scheduleChanged = false;
        boolean statusChanged = false;

        if (command.getTitle() != null) {
            String title = command.getTitle().trim();
            if (title.isEmpty()) {
                throw new BusinessException("PARAM_ERROR", "任务标题不能为空");
            }
            task.setTitle(title);
        }
        if (command.getNote() != null) {
            String note = trimToNull(command.getNote());
            // 空字符串用于显式清空；updateById 默认会忽略 null 字段
            task.setNote(note == null ? "" : note);
        }
        if (command.getDueAt() != null) {
            LocalDateTime dueAt = DateTimeUtil.parseFlexible(command.getDueAt());
            if (dueAt == null) {
                throw new BusinessException("PARAM_ERROR", "截止时间不能为空");
            }
            task.setDueAt(dueAt);
            scheduleChanged = true;
        }
        if (command.getRrule() != null) {
            String rrule = normalizeRrule(command.getRrule());
            task.setRrule(rrule == null ? "" : rrule);
            scheduleChanged = true;
        }
        if (command.getResponseWindowMinutes() != null) {
            validateWindow(command.getResponseWindowMinutes());
            task.setResponseWindowMinutes(command.getResponseWindowMinutes());
            scheduleChanged = true;
        }
        if (command.getPriority() != null) {
            validatePriority(command.getPriority());
            task.setPriority(command.getPriority());
        }
        if (command.getAllowNote() != null) {
            task.setAllowNote(command.getAllowNote());
        }
        if (hasText(command.getTz())) {
            task.setTz(command.getTz().trim());
        }
        if (command.getStatus() != null) {
            String status = command.getStatus().trim().toUpperCase(Locale.ROOT);
            if (!TASK_STATUSES.contains(status)) {
                throw new BusinessException("PARAM_ERROR", "任务状态不合法");
            }
            if (!status.equals(task.getStatus())) {
                task.setStatus(status);
                statusChanged = true;
            }
        }

        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);

        if (scheduleChanged || statusChanged) {
            occurrenceMapper.deletePending(taskId, userId);
            if ("ACTIVE".equals(task.getStatus())) {
                generateOccurrences(task);
            }
        }
        fillNextOccurrence(task);
        return toTaskDTO(task);
    }

    @Transactional
    public void archive(Long userId, Long taskId) {
        Task task = requireOwned(userId, taskId);
        task.setStatus("ARCHIVED");
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        occurrenceMapper.deletePending(taskId, userId);
    }

    public List<OccurrenceItemResponseDTO> occurrences(Long userId,
                                                       String fromText,
                                                       String toText,
                                                       String statusText,
                                                       Integer limit) {
        LocalDateTime from = DateTimeUtil.parseFlexible(fromText);
        LocalDateTime to = DateTimeUtil.parseFlexible(toText);
        List<String> statuses = parseStatuses(statusText);
        int size = Math.min(Math.max(limit == null ? 50 : limit, 1), 100);
        return occurrenceMapper.queryOccurrences(userId, from, to, statuses, size)
                .stream()
                .map(this::toOccurrenceDTO)
                .toList();
    }

    public Task requireOwned(Long userId, Long taskId) {
        if (taskId == null) {
            throw new BusinessException("PARAM_ERROR", "缺少任务 id");
        }
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("NOT_FOUND", "任务不存在");
        }
        if (!task.getUserId().equals(userId)) {
            throw new BusinessException("FORBIDDEN", "没有访问该任务的权限");
        }
        return task;
    }

    private void generateOccurrences(Task task) {
        int window = task.getResponseWindowMinutes() == null ? 120 : task.getResponseWindowMinutes();
        LocalDateTime now = LocalDateTime.now();
        for (LocalDateTime occurAt : buildOccurrenceTimes(task)) {
            TaskOccurrence occurrence = new TaskOccurrence();
            occurrence.setTaskId(task.getId());
            occurrence.setUserId(task.getUserId());
            occurrence.setOccurAt(occurAt);
            occurrence.setExpiresAt(occurAt.plusMinutes(window));
            occurrence.setStatus("PENDING");
            occurrence.setCreatedAt(now);
            try {
                occurrenceMapper.insert(occurrence);
            } catch (DuplicateKeyException ignored) {
                // (task_id, occur_at) 已存在，跳过
            }
        }
        fillNextOccurrence(task);
    }

    private List<LocalDateTime> buildOccurrenceTimes(Task task) {
        LocalDateTime start = task.getDueAt();
        String rrule = task.getRrule();
        List<LocalDateTime> result = new ArrayList<>();
        if (!hasText(rrule) || "NONE".equalsIgnoreCase(rrule)) {
            result.add(start);
            return result;
        }

        Map<String, String> rule = parseRule(rrule);
        String freq = rule.getOrDefault("FREQ", "").toUpperCase(Locale.ROOT);
        int interval = Math.max(parseInt(rule.get("INTERVAL"), 1), 1);
        int countLimit = Math.min(parseInt(rule.get("COUNT"), MAX_OCCURRENCES), MAX_OCCURRENCES);
        LocalDateTime end = start.plusDays(30);
        if (rule.containsKey("UNTIL")) {
            LocalDateTime until = DateTimeUtil.parseFlexible(rule.get("UNTIL"));
            if (until != null && until.isBefore(end)) {
                end = until;
            }
        }

        switch (freq) {
            case "DAILY" -> {
                for (LocalDateTime cursor = start;
                     !cursor.isAfter(end) && result.size() < countLimit;
                     cursor = cursor.plusDays(interval)) {
                    result.add(cursor);
                }
            }
            case "WEEKLY" -> {
                Set<DayOfWeek> byDay = parseByDay(rule.get("BYDAY"));
                if (byDay.isEmpty()) {
                    for (LocalDateTime cursor = start;
                         !cursor.isAfter(end) && result.size() < countLimit;
                         cursor = cursor.plusWeeks(interval)) {
                        result.add(cursor);
                    }
                } else {
                    LocalDateTime cursor = start;
                    while (!cursor.isAfter(end) && result.size() < countLimit) {
                        long weeks = ChronoUnit.WEEKS.between(start.toLocalDate(), cursor.toLocalDate());
                        if (weeks % interval == 0 && byDay.contains(cursor.getDayOfWeek())) {
                            result.add(cursor);
                        }
                        cursor = cursor.plusDays(1);
                    }
                }
            }
            case "MONTHLY" -> {
                int day = parseInt(rule.get("BYMONTHDAY"), start.getDayOfMonth());
                LocalDateTime cursor = start;
                while (!cursor.isAfter(end) && result.size() < countLimit) {
                    int safeDay = Math.min(Math.max(day, 1), cursor.toLocalDate().lengthOfMonth());
                    LocalDateTime candidate = cursor.withDayOfMonth(safeDay);
                    if (!candidate.isBefore(start)) {
                        result.add(candidate);
                    }
                    cursor = cursor.plusMonths(interval).withDayOfMonth(1);
                }
            }
            default -> result.add(start);
        }
        if (result.isEmpty()) {
            result.add(start);
        }
        return result;
    }

    private void fillNextOccurrence(Task task) {
        TaskOccurrence next = occurrenceMapper.selectNextOccurrence(task.getId());
        if (next == null) {
            task.setNextOccurrenceId(null);
            task.setNextOccurrenceAt(null);
            task.setNextOccurrenceStatus(null);
            return;
        }
        task.setNextOccurrenceId(next.getId());
        task.setNextOccurrenceAt(next.getOccurAt());
        task.setNextOccurrenceStatus(next.getStatus());
    }

    private TaskItemResponseDTO toTaskDTO(Task task) {
        TaskItemResponseDTO dto = new TaskItemResponseDTO();
        dto.setId(task.getId());
        dto.setTitle(task.getTitle());
        dto.setNote(task.getNote());
        dto.setDueAt(DateTimeUtil.format(task.getDueAt()));
        dto.setTz(task.getTz());
        dto.setRrule(task.getRrule());
        dto.setRruleLabel(rruleLabel(task.getRrule()));
        dto.setPriority(task.getPriority());
        dto.setPriorityLabel(priorityLabel(task.getPriority()));
        dto.setStatus(task.getStatus());
        dto.setStatusLabel(statusLabel(task.getStatus()));
        dto.setSource(task.getSource());
        dto.setResponseWindowMinutes(task.getResponseWindowMinutes());
        dto.setAllowNote(task.getAllowNote());
        dto.setNextOccurrenceId(task.getNextOccurrenceId());
        dto.setNextOccurrenceAt(DateTimeUtil.format(task.getNextOccurrenceAt()));
        dto.setNextOccurrenceStatus(task.getNextOccurrenceStatus());
        dto.setCreatedAt(DateTimeUtil.format(task.getCreatedAt()));
        return dto;
    }

    private OccurrenceItemResponseDTO toOccurrenceDTO(OccurrenceRow row) {
        LocalDateTime now = LocalDateTime.now();
        boolean responded = "RESPONDED".equals(row.getStatus());
        boolean canceled = "CANCELED".equals(row.getStatus());
        boolean canRespond = !responded && !canceled
                && row.getOccurAt() != null
                && !now.isBefore(row.getOccurAt())
                && (row.getExpiresAt() == null || !now.isAfter(row.getExpiresAt().plusHours(24)));
        boolean late = row.getExpiresAt() != null && now.isAfter(row.getExpiresAt());
        boolean canCorrect = responded
                && row.getRespondedAt() != null
                && row.getRevisedAt() == null
                && !now.isAfter(row.getRespondedAt().plusMinutes(10));

        OccurrenceItemResponseDTO dto = new OccurrenceItemResponseDTO();
        dto.setId(row.getId());
        dto.setTaskId(row.getTaskId());
        dto.setTitle(row.getTitle());
        dto.setNote(row.getNote());
        dto.setPriority(row.getPriority());
        dto.setPriorityLabel(priorityLabel(row.getPriority()));
        dto.setOccurAt(DateTimeUtil.format(row.getOccurAt()));
        dto.setExpiresAt(DateTimeUtil.format(row.getExpiresAt()));
        dto.setStatus(row.getStatus());
        dto.setStatusLabel(occurrenceStatusLabel(row.getStatus()));
        dto.setRespondedAt(DateTimeUtil.format(row.getRespondedAt()));
        dto.setResponseWindowMinutes(row.getResponseWindowMinutes());
        dto.setCanRespond(canRespond);
        dto.setCanCorrect(canCorrect);
        dto.setLate(late);
        dto.setCompletionRatio(row.getCompletionRatio());
        dto.setResponseNote(row.getResponseNote());
        dto.setIntimacyDelta(row.getIntimacyDelta());
        return dto;
    }

    private static List<String> parseStatuses(String text) {
        if (!hasText(text)) {
            return List.of();
        }
        Set<String> values = new HashSet<>();
        for (String part : text.split(",")) {
            String status = part.trim().toUpperCase(Locale.ROOT);
            if (status.isEmpty()) {
                continue;
            }
            if (!OCCURRENCE_STATUSES.contains(status)) {
                throw new BusinessException("PARAM_ERROR", "不支持的 occurrence 状态：" + part);
            }
            values.add(status);
        }
        return new ArrayList<>(values);
    }

    private static String normalizeTaskStatus(String status) {
        if (!hasText(status)) {
            return null;
        }
        String value = status.trim().toUpperCase(Locale.ROOT);
        if (!TASK_STATUSES.contains(value)) {
            throw new BusinessException("PARAM_ERROR", "不支持的任务状态：" + status);
        }
        return value;
    }

    private static String normalizeRrule(String rrule) {
        if (!hasText(rrule)) {
            return null;
        }
        String value = rrule.trim().toUpperCase(Locale.ROOT);
        if ("NONE".equals(value)) {
            return null;
        }
        if (!value.startsWith("FREQ=")) {
            if ("DAILY".equals(value) || "WEEKLY".equals(value) || "MONTHLY".equals(value)) {
                return "FREQ=" + value;
            }
            return value;
        }
        return value;
    }

    private static Map<String, String> parseRule(String rrule) {
        Map<String, String> rule = new HashMap<>();
        for (String part : rrule.split(";")) {
            int index = part.indexOf('=');
            if (index <= 0) {
                continue;
            }
            rule.put(part.substring(0, index).trim().toUpperCase(Locale.ROOT),
                    part.substring(index + 1).trim());
        }
        return rule;
    }

    private static Set<DayOfWeek> parseByDay(String value) {
        Set<DayOfWeek> days = new HashSet<>();
        if (!hasText(value)) {
            return days;
        }
        for (String part : value.split(",")) {
            String code = part.trim().toUpperCase(Locale.ROOT);
            DayOfWeek day = switch (code) {
                case "MO" -> DayOfWeek.MONDAY;
                case "TU" -> DayOfWeek.TUESDAY;
                case "WE" -> DayOfWeek.WEDNESDAY;
                case "TH" -> DayOfWeek.THURSDAY;
                case "FR" -> DayOfWeek.FRIDAY;
                case "SA" -> DayOfWeek.SATURDAY;
                case "SU" -> DayOfWeek.SUNDAY;
                default -> null;
            };
            if (day != null) {
                days.add(day);
            }
        }
        return days;
    }

    private static int parseInt(String value, int defaultValue) {
        if (!hasText(value)) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static void validatePriority(int priority) {
        if (priority < 1 || priority > 3) {
            throw new BusinessException("PARAM_ERROR", "优先级只能是 1~3");
        }
    }

    private static void validateWindow(int window) {
        if (window < 15 || window > 720) {
            throw new BusinessException("PARAM_ERROR", "回应窗口必须在 15 分钟到 12 小时之间");
        }
    }

    private static String rruleLabel(String rrule) {
        if (!hasText(rrule)) {
            return "不重复";
        }
        String value = rrule.toUpperCase(Locale.ROOT);
        if (value.contains("FREQ=DAILY")) {
            return "每天";
        }
        if (value.contains("FREQ=WEEKLY")) {
            return "每周";
        }
        if (value.contains("FREQ=MONTHLY")) {
            return "每月";
        }
        return "自定义";
    }

    private static String priorityLabel(Integer priority) {
        if (priority == null) {
            return "普通";
        }
        return switch (priority) {
            case 1 -> "低";
            case 3 -> "高";
            default -> "普通";
        };
    }

    private static String statusLabel(String status) {
        if (status == null) {
            return "-";
        }
        return switch (status) {
            case "ACTIVE" -> "进行中";
            case "PAUSED" -> "已暂停";
            case "ARCHIVED" -> "已归档";
            default -> status;
        };
    }

    private static String occurrenceStatusLabel(String status) {
        if (status == null) {
            return "-";
        }
        return switch (status) {
            case "PENDING" -> "待提醒";
            case "RINGING" -> "待回应";
            case "RESPONDED" -> "已回应";
            case "MISSED" -> "已错过";
            case "CANCELED" -> "已取消";
            default -> status;
        };
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
