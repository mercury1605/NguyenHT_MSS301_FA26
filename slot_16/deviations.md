# Slot 16 - DeThiSampleNew: deviations from spec / guide

| # | What | Spec / guide says | Implemented | Why |
|---|------|-------------------|-------------|-----|
| 1 | StudentID & location | `<StudentID>`, guide uses `SE123456` in `Code_PE/` | `DE191056`, projects directly in `slot_16/` | User decision |
| 2 | UserService PUT validation | Spec: partial update | Guide code: `@Valid` on PUT, so all mandatory fields must be sent | User chose to follow guide |
| 3 | `User.email` mapping | Guide: `unique = true` | No `unique` (matches `create_users_sql.txt`) | User decision; DB script has no unique on email |
| 4 | Lombok | Guide pom adds `lombok` (optional) | Not added; explicit getters/setters | User decision; spec says sample does not use Lombok |
| 5 | `DE191056UserController` constructor | Guide: `private` | `public` | Same behaviour, matches PostController style |
| 6 | PostService `application.properties` | Guide has no Jackson timezone | Added `spring.jackson.time-zone=Asia/Ho_Chi_Minh` | Bug in guide: `@JsonFormat` used UTC, GET returned dates one day early (DB 25/07 -> API 24/07). User approved |
| 7 | `PostServiceImpl.toLocalDate` | Guide: `date.toInstant()` | `Instant.ofEpochMilli(date.getTime())` | Bug in guide: `java.sql.Date.toInstant()` throws, partial PUT without both dates returned 500. User approved |
| 8 | Local test DB password | `spring.datasource.password=sa` | Kept `sa` in files; local docker SQL Server needs a strong password, so tests ran with env `SPRING_DATASOURCE_PASSWORD` | Environment only, no code change |
