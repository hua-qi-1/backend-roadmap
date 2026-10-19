# backend-roadmap

Java 后端实习冲刺仓库：学习笔记 + 练习代码 + 项目。

- 起点：2026 年 10 月（大三上）
- 目标：2027 年 3–5 月拿到暑期实习 offer
- 完整计划见 [docs/后端实习学习计划.md](docs/后端实习学习计划.md)

## 目录

| 目录 | 内容 |
|---|---|
| `docs/` | 学习计划、每周笔记、面经复盘 |
| `docs/notes/` | 每日/每题笔记（面试前速查） |
| `hello-maven/` | 环境验证项目：Maven + JDK 跑通的第一个工程 |
| `java-se/leetcode/` | 刷题代码（Java 版，含自测 main） |
| `java-se/` | 集合、并发、JVM 的练习代码（待补） |
| `mysql/` | 索引、事务、执行计划实验（待建） |
| `redis/` | 缓存与分布式锁练习（待建） |

## 进度

- [x] 2026-10-18 环境搭建：JDK 23 / Maven 3.9.16 / 阿里云镜像 / `mvn package` 验证通过
- [x] 2026-10-18 建立远程仓库 https://github.com/hua-qi-1/backend-roadmap 并完成首个提交
- [x] 2026-10-18 晚：LeetCode 两数之和 → `java-se/leetcode/TwoSum.java`（哈希 O(n) + 暴力对照 + 笔记）
- [x] 2026-10-18 晚：HashMap 源码过完（结构 / put / 扩容 / 树化 + 自测 5 题）
- [x] 2026-10-19 Redis 5.0.14.1 装好（`C:\dev\redis`，已在 PATH + 开机自启，`PING` → `PONG`）
- [x] 2026-10-19 晚：ArrayList 源码过完（1.5 倍扩容 / fail-fast / 与 LinkedList 对比 + 自测 6 题）
- [ ] 2026-10-21 前：Java 集合（ArrayList ✅ / HashMap ✅）→ 接着进并发篇

## 笔记索引

| 日期 | 主题 | 笔记 |
|---|---|---|
| 2026-10-18 | LeetCode 1. 两数之和 | [leetcode-两数之和.md](docs/notes/2026-10-18-leetcode-两数之和.md) |
| 2026-10-18 | Java HashMap 源码 | [java-hashmap.md](docs/notes/2026-10-18-java-hashmap.md) |
| 2026-10-19 | Redis 环境搭建 | [redis-setup.md](docs/notes/2026-10-19-redis-setup.md) |
| 2026-10-19 | Java ArrayList 源码 | [2026-10-19-arraylist.md](docs/notes/2026-10-19-arraylist.md) |