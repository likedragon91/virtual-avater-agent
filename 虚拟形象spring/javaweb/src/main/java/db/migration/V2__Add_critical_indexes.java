package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;

/**
 * 为已有数据库幂等补充关键索引。
 *
 * 新库的索引已写在 V1 基线中，本迁移会检测并跳过；
 * 已有库（历史上手工建表）会补上缺失的索引。
 */
public class V2__Add_critical_indexes extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();

        // 用户
        ensureIndex(connection, "user", "uk_user_username", true, false,
                List.of("username"),
                "ALTER TABLE `user` ADD UNIQUE INDEX `uk_user_username` (`username`)");
        ensureIndex(connection, "user", "uk_user_email", true, false,
                List.of("email"),
                "ALTER TABLE `user` ADD UNIQUE INDEX `uk_user_email` (`email`)");
        ensureIndex(connection, "user", "idx_user_status_id", false, false,
                List.of("status", "id"),
                "ALTER TABLE `user` ADD INDEX `idx_user_status_id` (`status`, `id`)");
        ensureIndex(connection, "user", "idx_user_type_id", false, false,
                List.of("user_type", "id"),
                "ALTER TABLE `user` ADD INDEX `idx_user_type_id` (`user_type`, `id`)");

        // 亲密度
        ensureIndex(connection, "intimacy_log", "idx_intimacy_user_id", false, false,
                List.of("user_id", "id"),
                "ALTER TABLE `intimacy_log` ADD INDEX `idx_intimacy_user_id` (`user_id`, `id`)");
        ensureIndex(connection, "stage_event", "uk_stage_event_user_to", true, false,
                List.of("user_id", "to_stage"),
                "ALTER TABLE `stage_event` ADD UNIQUE INDEX `uk_stage_event_user_to` (`user_id`, `to_stage`)");

        // 配置表
        ensureIndex(connection, "stage_policy", "idx_stage_policy_code_active", false, false,
                List.of("code", "active"),
                "ALTER TABLE `stage_policy` ADD INDEX `idx_stage_policy_code_active` (`code`, `active`)");
        ensureIndex(connection, "prompt_template", "idx_prompt_code_active", false, false,
                List.of("code", "active", "version"),
                "ALTER TABLE `prompt_template` ADD INDEX `idx_prompt_code_active` (`code`, `active`, `version`)");

        // 会话与消息
        ensureIndex(connection, "chat_session", "idx_chat_session_user_status_active", false, false,
                List.of("user_id", "status", "last_active_at"),
                "ALTER TABLE `chat_session` ADD INDEX `idx_chat_session_user_status_active` "
                        + "(`user_id`, `status`, `last_active_at`)");
        ensureIndex(connection, "chat_message", "uk_message_client", true, false,
                List.of("session_id", "client_message_id"),
                "ALTER TABLE `chat_message` ADD UNIQUE INDEX `uk_message_client` "
                        + "(`session_id`, `client_message_id`)");
        ensureIndex(connection, "chat_message", "idx_chat_message_session_user_status_id", false, false,
                List.of("session_id", "user_id", "status", "id"),
                "ALTER TABLE `chat_message` ADD INDEX `idx_chat_message_session_user_status_id` "
                        + "(`session_id`, `user_id`, `status`, `id`)");
        ensureIndex(connection, "chat_message", "idx_chat_message_user_created", false, false,
                List.of("user_id", "created_at"),
                "ALTER TABLE `chat_message` ADD INDEX `idx_chat_message_user_created` (`user_id`, `created_at`)");

        // 工具日志
        ensureIndex(connection, "tool_call_log", "idx_tool_message", false, false,
                List.of("message_id"),
                "ALTER TABLE `tool_call_log` ADD INDEX `idx_tool_message` (`message_id`)");
        ensureIndex(connection, "tool_call_log", "idx_tool_user_time", false, false,
                List.of("user_id", "created_at"),
                "ALTER TABLE `tool_call_log` ADD INDEX `idx_tool_user_time` (`user_id`, `created_at`)");
        ensureIndex(connection, "tool_call_log", "idx_tool_session", false, false,
                List.of("session_id"),
                "ALTER TABLE `tool_call_log` ADD INDEX `idx_tool_session` (`session_id`)");

        // 记忆
        ensureIndex(connection, "memory_item", "ft_memory_content", false, true,
                List.of("content"),
                "ALTER TABLE `memory_item` ADD FULLTEXT INDEX `ft_memory_content` (`content`) WITH PARSER ngram");
        ensureIndex(connection, "memory_item", "idx_memory_user_status_type", false, false,
                List.of("user_id", "status", "memory_type"),
                "ALTER TABLE `memory_item` ADD INDEX `idx_memory_user_status_type` "
                        + "(`user_id`, `status`, `memory_type`)");
        ensureIndex(connection, "memory_item", "idx_memory_user_status_order", false, false,
                List.of("user_id", "status", "importance", "occurred_at", "created_at"),
                "ALTER TABLE `memory_item` ADD INDEX `idx_memory_user_status_order` "
                        + "(`user_id`, `status`, `importance`, `occurred_at`, `created_at`)");

        // 任务
        ensureIndex(connection, "task", "idx_task_user_status_due", false, false,
                List.of("user_id", "status", "due_at"),
                "ALTER TABLE `task` ADD INDEX `idx_task_user_status_due` (`user_id`, `status`, `due_at`)");
        ensureIndex(connection, "task", "idx_task_user_due", false, false,
                List.of("user_id", "due_at"),
                "ALTER TABLE `task` ADD INDEX `idx_task_user_due` (`user_id`, `due_at`)");
        ensureIndex(connection, "task_occurrence", "uk_task_occurrence_task_time", true, false,
                List.of("task_id", "occur_at"),
                "ALTER TABLE `task_occurrence` ADD UNIQUE INDEX `uk_task_occurrence_task_time` (`task_id`, `occur_at`)");
        ensureIndex(connection, "task_occurrence", "idx_task_occurrence_status_time", false, false,
                List.of("status", "occur_at", "expires_at"),
                "ALTER TABLE `task_occurrence` ADD INDEX `idx_task_occurrence_status_time` "
                        + "(`status`, `occur_at`, `expires_at`)");
        ensureIndex(connection, "task_occurrence", "idx_task_occurrence_status_expires", false, false,
                List.of("status", "expires_at"),
                "ALTER TABLE `task_occurrence` ADD INDEX `idx_task_occurrence_status_expires` "
                        + "(`status`, `expires_at`)");
        ensureIndex(connection, "task_occurrence", "idx_task_occurrence_user_time_status", false, false,
                List.of("user_id", "occur_at", "status"),
                "ALTER TABLE `task_occurrence` ADD INDEX `idx_task_occurrence_user_time_status` "
                        + "(`user_id`, `occur_at`, `status`)");
        ensureIndex(connection, "task_occurrence", "idx_task_occurrence_task_status_time", false, false,
                List.of("task_id", "status", "occur_at"),
                "ALTER TABLE `task_occurrence` ADD INDEX `idx_task_occurrence_task_status_time` "
                        + "(`task_id`, `status`, `occur_at`)");
        ensureIndex(connection, "task_response", "uk_task_response_occurrence", true, false,
                List.of("occurrence_id"),
                "ALTER TABLE `task_response` ADD UNIQUE INDEX `uk_task_response_occurrence` (`occurrence_id`)");
        ensureIndex(connection, "task_response", "uk_task_response_idempotency", true, false,
                List.of("idempotency_key"),
                "ALTER TABLE `task_response` ADD UNIQUE INDEX `uk_task_response_idempotency` (`idempotency_key`)");
        ensureIndex(connection, "task_response", "idx_task_response_task", false, false,
                List.of("task_id"),
                "ALTER TABLE `task_response` ADD INDEX `idx_task_response_task` (`task_id`)");
        ensureIndex(connection, "task_response", "idx_task_response_user_responded", false, false,
                List.of("user_id", "responded_at"),
                "ALTER TABLE `task_response` ADD INDEX `idx_task_response_user_responded` (`user_id`, `responded_at`)");

        // 通知与推送
        ensureIndex(connection, "notification", "idx_notification_user_id", false, false,
                List.of("user_id", "id"),
                "ALTER TABLE `notification` ADD INDEX `idx_notification_user_id` (`user_id`, `id`)");
        ensureIndex(connection, "notification", "idx_notification_user_read", false, false,
                List.of("user_id", "read_at"),
                "ALTER TABLE `notification` ADD INDEX `idx_notification_user_read` (`user_id`, `read_at`)");
        ensureIndex(connection, "push_subscription", "uk_push_endpoint", true, false,
                List.of("endpoint"),
                "ALTER TABLE `push_subscription` ADD UNIQUE INDEX `uk_push_endpoint` (`endpoint`)");
        ensureIndex(connection, "push_subscription", "idx_push_user_id", false, false,
                List.of("user_id", "id"),
                "ALTER TABLE `push_subscription` ADD INDEX `idx_push_user_id` (`user_id`, `id`)");

        // LLM 日志
        ensureIndex(connection, "llm_call_log", "idx_llm_user_created", false, false,
                List.of("user_id", "created_at"),
                "ALTER TABLE `llm_call_log` ADD INDEX `idx_llm_user_created` (`user_id`, `created_at`)");
        ensureIndex(connection, "llm_call_log", "idx_llm_trace", false, false,
                List.of("trace_id"),
                "ALTER TABLE `llm_call_log` ADD INDEX `idx_llm_trace` (`trace_id`)");
    }

    private void ensureIndex(Connection connection,
                             String table,
                             String indexName,
                             boolean unique,
                             boolean fulltext,
                             List<String> columns,
                             String ddl) throws SQLException {
        if (!tableExists(connection, table)) {
            System.out.println("[flyway] skip index " + indexName + ": table " + table + " not found");
            return;
        }
        if (indexExists(connection, table, indexName, columns, unique, fulltext)) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute(ddl);
            System.out.println("[flyway] added index " + indexName + " on " + table);
        }
    }

    private boolean tableExists(Connection connection, String table) throws SQLException {
        String sql = "SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema = DATABASE() AND table_name = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, table);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getInt(1) > 0;
            }
        }
    }

    private boolean indexExists(Connection connection,
                                String table,
                                String indexName,
                                List<String> columns,
                                boolean unique,
                                boolean fulltext) throws SQLException {
        String sql = "SELECT INDEX_NAME, "
                + "GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS columns_csv, "
                + "MIN(NON_UNIQUE) AS non_unique, MIN(INDEX_TYPE) AS index_type "
                + "FROM information_schema.statistics "
                + "WHERE table_schema = DATABASE() AND table_name = ? "
                + "GROUP BY INDEX_NAME";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, table);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String existingName = resultSet.getString("INDEX_NAME");
                    if (indexName.equalsIgnoreCase(existingName)) {
                        return true;
                    }
                    String csv = resultSet.getString("columns_csv");
                    if (csv == null) {
                        continue;
                    }
                    List<String> existingColumns = Arrays.stream(csv.split(","))
                            .map(String::trim)
                            .toList();
                    boolean existingUnique = resultSet.getInt("non_unique") == 0;
                    boolean existingFulltext = "FULLTEXT".equalsIgnoreCase(resultSet.getString("index_type"));
                    if (existingColumns.equals(columns)
                            && existingUnique == unique
                            && existingFulltext == fulltext) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
