# Redis 环境搭建（2026-10-19 · Windows 便携版）

## 一、装好了什么

| 项 | 值 |
|---|---|
| 版本 | **Redis 5.0.14.1**（[tporadowski/redis](https://github.com/tporadowski/redis) 的 Windows 构建） |
| 位置 | `C:\dev\redis`（解压即用，无安装程序） |
| 端口 | `6379`，已监听 |
| 监听地址 | `bind 127.0.0.1`（只有本机能连，安全默认值） |
| 密码 | 无（学习环境够用；要加：`CONFIG SET requirepass 你的密码`） |
| 数据文件 | `C:\dev\redis\dump.rdb`（RDB 快照，`save 900 1 / 300 10 / 60 10000`） |
| AOF | 关闭（`appendonly no`） |
| PATH | 用户 PATH 已追加 `C:\dev\redis`，新开的终端可直接用 `redis-cli` |
| 开机自启 | `HKCU\Software\Microsoft\Windows\CurrentVersion\Run` → `wscript.exe "C:\dev\redis\start-redis-hidden.vbs"`（隐藏窗口） |

## 二、为什么是便携版，不是 Memurai

Memurai（Windows 原生 Redis 兼容服务）确实更正规，但它的 **MSI 安装需要管理员权限**；当前会话的 Administrators 组是 **deny-only**（UAC 提权拿不到令牌），静默安装必然失败。

便携版的好处：

- 免管理员，解压即用，5 分钟搞定
- 数据、配置都在 `C:\dev\redis` 下，删目录即卸载，不污染系统
- 命令与 Linux/线上完全一致（`redis-cli`、`SET`、`EXPIRE`、Lua 都能跑）

代价：

- 不是 Windows 服务，靠登录时启动脚本拉起（脚本挂了要手动开 `start-redis.bat`）
- 版本是 5.0，比线上常用的 7.x 老：**没有 ACL（多用户权限）**、没有 6.0 的多线程 I/O、`RESET`/`CLIENT NO-EVICT` 之类新命令不支持
- 学「缓存 + 分布式锁 + Lua」完全够用；等要上 Docker/WSL2 做项目时再换

## 三、安装步骤（可重现）

```powershell
# 1. 下载（12 MB）
$url = 'https://github.com/tporadowski/redis/releases/download/v5.0.14.1/Redis-x64-5.0.14.1.zip'
# 2. 解压到 C:\dev\redis
Expand-Archive -Path $zip -DestinationPath 'C:\dev\redis' -Force
# 3. 启动（后台，无窗口）
Invoke-CimMethod -ClassName Win32_Process -MethodName Create `
  -Arguments @{ CommandLine = '"C:\dev\redis\redis-server.exe" "C:\dev\redis\redis.windows.conf"' }
# 4. 验证
C:\dev\redis\redis-cli.exe ping     # 期望 PONG
```

zip 的 SHA256：`018EA18A35876383CBB5F4CD0258ADFC87747CF9D619BCE1CF73A2E36F720CCF`

## 四、日常操作

| 场景 | 做法 |
|---|---|
| 启动（有窗口，能看到日志） | 双击 `C:\dev\redis\start-redis.bat` |
| 停止 | 双击 `C:\dev\redis\stop-redis.bat`，或 `redis-cli shutdown nosave` |
| 连上去敲命令 | 任意终端输入 `redis-cli`（已在 PATH；旧终端要新开） |
| 关掉开机自启 | 删注册表项 `HKCU:\Software\Microsoft\Windows\CurrentVersion\Run` 里的 `RedisServer` |
| 看服务状态 | `redis-cli info server` / `tasklist | findstr redis-server` |

## 五、验证记录

```
> redis-cli ping
PONG
> redis-cli set name xiaozhang      → OK
> redis-cli get name                → "xiaozhang"
> redis-cli setex tmp 60 hello      → OK, ttl → 60
> redis-cli incr counter            → 1, 再执行 → 2
> redis-cli eval "return redis.call('set', KEYS[1], ARGV[1])" 1 lua_key lua_val
                                    → lua_val（Lua 脚本可用，后面做分布式锁要用）
> redis-cli info server             → redis_version:5.0.14.1, tcp_port:6379
> redis-cli bgsave                  → C:\dev\redis\dump.rdb 生成（181 字节）
```

## 六、常用命令速查

| 类型 | 命令 |
|---|---|
| 键 | `DEL` `EXISTS` `EXPIRE` `TTL` `PERSIST` `TYPE` `KEYS *`（线上禁用）`SCAN 0 MATCH a* COUNT 100` |
| String | `SET k v EX 60 NX` `GET` `MSET` `INCR` `INCRBY` `SETNX` `GETSET` |
| Hash | `HSET h f v` `HGET` `HGETALL` `HDEL` `HINCRBY` |
| List | `LPUSH` `RPUSH` `LPOP` `RPOP` `LRANGE 0 -1` `LLEN` |
| Set | `SADD` `SMEMBERS` `SISMEMBER` `SINTER` `SPOP` |
| ZSet | `ZADD` `ZRANGE 0 -1 WITHSCORES` `ZREVRANGE` `ZINCRBY` `ZSCORE` |
| 事务 | `MULTI` → 命令入队 → `EXEC` / `DISCARD` |
| 其它 | `INFO` `DBSIZE` `FLUSHDB` `CONFIG GET/SET` `SLOWLOG GET` |

> 面试常问：`SETNX + EXPIRE` 不是原子的 → 用 `SET k v NX EX 10` 一条命令搞定；复杂逻辑用 Lua 保证原子性。

## 七、踩坑记录

1. **`Start-Process -WindowStyle Hidden` 启动 redis-server 会让整个 PowerShell 进程直接退出（exit -1）**，改用 `Invoke-CimMethod Win32_Process.Create` 创建进程，正常。
2. **`dir` 默认是 `./`（相对启动时的工作目录）**，通过 WMI 启动时工作目录是 `C:\WINDOWS\system32`，`dump.rdb` 会写错地方。已把 `redis.windows.conf` 里改成绝对路径 `dir "C:\dev\redis"`，并 `CONFIG SET dir` 立即生效。
3. 用户 PATH 改完，**已经打开的终端读不到**，要新开一个窗口才有 `redis-cli`。
