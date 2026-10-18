# Java HashMap 源码笔记（2026-10-18 · JDK 23）

源码：`$JAVA_HOME\lib\src.zip` → `java.base/java/util/HashMap.java`（2588 行）
本地已解压一份方便查：[`HashMap.java`](../../.jdk-src/HashMap.java)（`C:\Users\001\Desktop\新建文件夹\.jdk-src\HashMap.java`）

## 一、一句话本质

> `HashMap = Node[] 数组 + 链表 + 红黑树`。用 `hash(key)` 算出下标直接定位；冲突了就在同一个桶里挂链表；链表太长转红黑树。

```java
static class Node<K,V> implements Map.Entry<K,V> {   // HashMap.java:281
    final int hash;     // key 的哈希（缓存一份，避免每次重算）
    final K key;
    V value;            // 唯一非 final —— put 同一个 key 就是改这里
    Node<K,V> next;     // 拉链指针，指向同桶下一个节点
}
transient Node<K,V>[] table;                         // HashMap.java:390 桶数组
```

```
table ┌────┬────┬────┬────┬────┐
      │null│ ●  │null│ ●  │null│      下标 i = (n-1) & hash
      └────┴─┬──┴────┴─┬──┴────┘
             ▼          ▼
           [k1,v1]    [k5,v5]
             │          │
           [k2,v2]   (红黑树)
```

## 二、key → 下标 的三步

### 第 1 步：扰动 `hash()`（HashMap.java:336）

```java
static final int hash(Object key) {
    int h;
    return (key == null) ? 0 : (h = key.hashCode()) ^ (h >>> 16);
}
```

- **为什么要异或高 16 位**：取下标只用到 hash 的低几位（`n-1` 掩码），高位被丢弃。若一批 key 的 hashCode 只在高位不同（源码注释的例子：小表里连续整数的 `Float` key），会全部撞进同一个桶。`h ^ (h >>> 16)` 把高 16 位掺进低 16 位，用一次异或改善散列，成本几乎为零。
- **null key** 的 hash 固定为 0，永远落 0 号桶。所以 `HashMap` 允许 1 个 null key；`Hashtable` / `ConcurrentHashMap` 不允许。

### 第 2 步：定位桶（HashMap.java:637）

```java
if ((p = tab[i = (n - 1) & hash]) == null)
```

**为什么容量必须是 2 的幂**：

- `n = 16` 时 `n-1 = 0b1111`，`hash & 15` 等价于 `hash % 16`，但 `&` 比 `%` 快一个数量级
- 等价性**只在 2 的幂时成立**；`n = 15` 时 `0b1110` 末位永远取不到 1，一半桶废掉
- 所以 `new HashMap<>(10)` 实际容量是 **16**：构造器经 `tableSizeFor` 向上取到最近的 2 的幂（HashMap.java:379 那段 `n |= n >>> 1...` 位运算）

### 第 3 步：查找/插入时判断「是不是同一个 key」

必须同时满足：`hash` 相同 **且**（`==` 或 `equals` 相等）。

## 三、关键常量表

| 常量 | 值 | 位置 | 为什么是这个值 |
|---|---|---|---|
| `DEFAULT_INITIAL_CAPACITY` | 16 | :238 | `1 << 4`，满足 2 的幂；太小频繁扩容，太大浪费 |
| `DEFAULT_LOAD_FACTOR` | 0.75 | :250 | 空间 ↔ 冲突率的折中；该值下元素分布最均匀、冲突概率最低 |
| `TREEIFY_THRESHOLD` | 8 | :260 | 理想随机 hash 下桶内节点数服从泊松分布，链长到 8 的概率约 6/1e8 —— 正常到不了，到了说明 hash 被构造 |
| `UNTREEIFY_THRESHOLD` | 6 | :267 | 比 8 小，留缓冲，防止 7↔8 反复树化/退化抖动 |
| `MIN_TREEIFY_CAPACITY` | 64 | :275 | 表还小的时候扩容比树化更划算（扩容后链会拆成两条） |

## 四、put 流程（`putVal`，HashMap.java:632-673）

1. `table` 为 null/空 → `resize()` 初始化成 16
2. 目标桶为 null → `newNode` 直接放入（最快路径）
3. 桶头 hash 相同且 (`==` 或 `equals`) → 同一 key，准备替换 value
4. 桶头是 `TreeNode` → `putTreeVal` 红黑树插入
5. 否则遍历链表：
   - 走到尾部 → 尾插新节点；插入时 `binCount >= TREEIFY_THRESHOLD - 1`（已 8 个）→ `treeifyBin`
   - 途中发现同 key → 跳出，准备替换 value
6. 替换 value（`onlyIfAbsent=false` 时覆盖），**返回旧值**
7. `++size > threshold` → `resize()`

### 树化条件（`treeifyBin`，HashMap.java:762-765）

```java
if (tab == null || (n = tab.length) < MIN_TREEIFY_CAPACITY)
    resize();               // 表 < 64 时优先扩容，而不是树化
else if ((e = tab[index = (n - 1) & hash]) != null) { ... hd.treeify(tab); }
```

> 准确说法：**链表长度 ≥ 8 且数组长度 ≥ 64 才树化，否则先扩容**。

## 五、扩容：2 倍 + 下标只可能「不变」或「+oldCap」

`resize()`（HashMap.java:684-756），核心在 :722-750：

```java
} else { // preserve order
    Node<K,V> loHead, loTail;   // 留在原下标 j
    Node<K,V> hiHead, hiTail;   // 挪到 j + oldCap
    ...
        if ((e.hash & oldCap) == 0)  ...低位链
        else                         ...高位链
    ...
    newTab[j] = loHead;
    newTab[j + oldCap] = hiHead;
```

**原理**：容量 16 → 32，掩码 `0b1111` → `0b11111`，只多出**第 5 位**。`oldCap` 正好是 `0b10000`，所以：

- `e.hash & oldCap == 0` → 该位是 0 → 下标仍为 `j`
- `e.hash & oldCap != 0` → 该位是 1 → 下标变 `j + oldCap`

**这就是 2 倍扩容的红利：不用重算 hash、不用取模，O(1) 判断每个元素的去向。**

**JDK 7 → 8 的修复**：JDK 7 头插法在多线程并发扩容时会把链表插成**环**，之后 `get` 死循环（CPU 100%）。JDK 8 改为尾插 + lo/hi 双链（`// preserve order` 注释即为此），不会成环，但**仍然不是线程安全的**：并发 put 同一空桶会互相覆盖丢数据、`size` 也会算错。

## 六、get 流程（`getNode`，HashMap.java:574）

`hash` → 定位桶 → 桶头匹配则返回 → 是 TreeNode 则树查找 → 否则遍历链表。平均 **O(1)**，最坏 O(log n)（树化后）。

## 七、面试最易错的六个点

| 说法 | 正确表述 |
|---|---|
| ❌ 链表到 8 就转红黑树 | 还要 `table.length >= 64`，否则先扩容 |
| ❌ 容量就是构造器传的数 | 向上取最近的 2 的幂，`new HashMap<>(10)` 容量 16 |
| ❌ JDK8 不会死循环了 ⇒ 线程安全 | 只是不成环；丢数据、覆盖照样有 → 并发用 `ConcurrentHashMap` |
| ❌ hashCode 相同就是同一个 key | 还要 `equals` 相等；hashCode 同可能不同 key，equals 同必然 hashCode 同 |
| ❌ 扩容 1.5 倍 | 2 倍（`oldCap << 1`），保证掩码只多一位 |
| ❌ 负载因子 0.75 随便定的 | 空间/冲突率折中，配合泊松分布把树化概率压到 6e-8 量级 |

## 八、实践要点

- **预设容量**：已知要存 n 个元素，用 `new HashMap<>((int)(n / 0.75f) + 1)`，避免反复扩容（Guava 的 `Maps.newHashMapWithExpectedSize` 同理）
- **自定义对象作 key 必须同时重写 `hashCode` 与 `equals`**，否则全落 0 号桶退化成链表
- **遍历时不能 `put`/`remove`**，会 `ConcurrentModificationException`（`modCount` 校验）；要删除用 `Iterator.remove()`，要修改先收集再处理
- `HashMap` 的迭代顺序不保证；要顺序用 `LinkedHashMap`（插入序/访问序，可实现 LRU）、要排序用 `TreeMap`
- 线程安全：`ConcurrentHashMap`（分段/CAS + synchronized 桶头，JDK 8 起）

## 九、自测 5 题（答案在文末）

1. `new HashMap<>(10)` 的 `table.length` 是多少？存到第几个元素时扩容？
2. 容量 16 时，某个 key 的 hash 是 `0b1_0000_0101`，它扩容到 32 后落在哪个下标？
3. 为什么 `UNTREEIFY_THRESHOLD = 6` 而不是 8？
4. JDK 8 的 HashMap 在多线程下还会死循环吗？会出什么问题？
5. 下面代码输出什么？为什么？

```java
Map<Object, String> m = new HashMap<>();
m.put(new String("a"), "x");
m.put(new String("a"), "y");
System.out.println(m.size() + " " + m.get(new String("a")));
```

---

### 自测答案

1. 容量 **16**（向上取 2 的幂）；阈值 `16 * 0.75 = 12`，所以**放入第 13 个元素时**触发扩容到 32（源码是 `++size > threshold` 后 `resize()`，即 size 变 13 时）。
2. 低位判断：`hash & 16 = 0b1_0000_0101 & 0b1_0000 = 0b1_0000 ≠ 0` → 落到 **`j + oldCap` = 21**（原下标 `hash & 15 = 5`）。
3. 防止 7↔8 边界反复「树化 → 退化 → 树化」抖动，留出 6/7 的缓冲带，减少结构调整开销。
4. **不会成环死循环**（JDK 8 尾插 + lo/hi 拆链）；但仍会**丢数据（覆盖）、`size` 不准、并发读写可见性问题**，所以不能用，要用 `ConcurrentHashMap`。
5. 输出 **`1 y`**。两个 `new String("a")` 是不同对象（`==` 为 false），但 `String` 重写了 `hashCode`/`equals`，所以算同一个 key —— 第二次 put 覆盖 value，size 仍为 1。**这正好证明：判等靠 `equals`，`==` 不够。**
