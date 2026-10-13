# 后端（老师项目基线）

Java 21、Spring Boot 3.2、Spring Data JPA、MySQL、Spring Security / JWT。启动配置见根目录 [DEVELOPMENT](../DEVELOPMENT.md)。

源码中的接口包括 `/api/users`、`/api/demands`、`/api/messages`、`/api/notifications`、`/api/reviews`、`/api/appeals`。

需求动作：POST /api/demands 发布，PUT /api/demands/{id}/accept 接单，/complete 提交凭证，/confirm 发布者确认完成，/cancel 取消。状态 PENDING → IN_PROGRESS → PENDING_CONFIRMATION → COMPLETED，另有 CANCELLED。具体角色限制以服务层为准。

BORROW 和 STUDY 目前仍使用统一 DemandRequest；新增个性化字段与 AI 对话属于后续升级。支付只更新 paymentStatus，未发现真实资金接口。短信默认模拟。
