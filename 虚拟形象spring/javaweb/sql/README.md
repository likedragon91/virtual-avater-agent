# 历史 SQL 脚本（仅作记录）

本目录中的脚本是 Flyway 接入前的历史手工迁移：

- `agent_chat_migration.sql`
- `alter_user_align_with_entity.sql`
- `persona_seed.sql`

**部署和新建数据库不要再执行这些脚本。**

数据库结构的唯一事实来源已经改为：

```text
src/main/resources/db/migration/
├── V1__baseline_schema.sql
└── V3__seed_persona_and_stage_policy.sql

src/main/java/db/migration/
└── V2__Add_critical_indexes.java   # Java 迁移，由 Flyway 从 classpath:db/migration 发现
```

> V2 是 Java 迁移（`BaseJavaMigration`），与 SQL 迁移同属 `db/migration` 命名空间；不要移动或重命名已执行的迁移，否则 Flyway 校验会失败。

应用启动时由 Flyway 自动执行：

- 新库：自动建表、补索引、写种子数据。
- 已有库：`baseline-on-migrate=true` 接管后，补缺失索引和种子。
- 迁移记录表：`flyway_schema_history`。

新增表或字段时，请新增版本化迁移文件，不要修改已经执行过的 V1/V2/V3。
