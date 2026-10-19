# Java ArrayList 源码笔记（2026-10-19 · JDK 23）

源码：`java.base/java/util/ArrayList.java`（1815 行）
本地已解压：[`ArrayList.java`](../../.jdk-src/ArrayList.java)、[`ArraysSupport.java`](../../.jdk-src/ArraysSupport.java)

## 一、一句话本质

> `ArrayList = Object[] elementData + int size`，**数组能随机访问**，所以 `get`/`set` 是 O(1)；尾部追加均摊 O(1)；中间插入删除要搬元素，O(n)。

```java
transient Object[] elementData;   // ArrayList.java:139  真正存东西的数组
private int size;                 // ArrayList.java:146  实际元素个数（≠ 数组长度！）
```

**`size` 和容量是两个东西**：`size` 是元素个数，`elementData.length` 是容量。`new ArrayList<>(100)` 后 `size()==0` 但容量已是 100。

## 二、三个"空数组"常量与懒初始化

```java
private static final int DEFAULT_CAPACITY = 10;                        // :119
private static final Object[] EMPTY_ELEMENTDATA = {};                  // :124
private static final Object[] DEFAULTCAPACITY_EMPTY_ELEMENTDATA = {};  // :131
```

| 构造方式 | elementData 初始值 | 第一次 add 后容量 |
|---|---|---|
| `new ArrayList<>()` | `DEFAULTCAPACITY_EMPTY_ELEMENTDATA` | **10**（`Math.max(DEFAULT_CAPACITY, minCapacity)`） |
| `new ArrayList<>(0)` | `EMPTY_ELEMENTDATA` | **1**（按需长，不从 10 开始） |
| `new ArrayList<>(30)` | `new Object[30]` | 30（立即分配） |
| `new ArrayList<>(collection)` | `toArray()` 结果 | 现有元素个数 |

为什么要有两个空数组？因为"默认构造"和"显式容量 0"要区分开：前者第一次 add 应该扩容到 10，后者只该扩到 1。**用同一个空数组就分不清这两种意图了。** 源码注释（:128）原话：`distinguish this from EMPTY_ELEMENTDATA to know how much to inflate when first element is added`。

> 面试点：`new ArrayList<>(10)` 和 `new ArrayList<>()` 的区别在"第一次 add 时扩容到多少"，后者还要看是不是默认构造。

## 三、add(E)：三步

```java
public boolean add(E e) {           // :495
    modCount++;
    add(e, elementData, size);      // size 作为写入位置
    return true;
}

private void add(E e, Object[] elementData, int s) {   // :482
    if (s == elementData.length)
        elementData = grow();       // 满了才扩容
    elementData[s] = e;
    size = s + 1;
}
```

> 有意思的细节：`add(E)` 被拆成两个方法，源码注释说这是为了让字节码小于 35 字节（`-XX:MaxInlineSize` 默认值），方便 C1 在循环里内联 `add`。

## 四、扩容：1.5 倍（不是 2 倍）

```java
private Object[] grow(int minCapacity) {          // :232
    int oldCapacity = elementData.length;
    if (oldCapacity > 0 || elementData != DEFAULTCAPACITY_EMPTY_ELEMENTDATA) {
        int newCapacity = ArraysSupport.newLength(oldCapacity,
                minCapacity - oldCapacity,  /* minimum growth */
                oldCapacity >> 1            /* preferred growth = 旧容量的一半 */);
        return elementData = Arrays.copyOf(elementData, newCapacity);
    } else {
        return elementData = new Object[Math.max(DEFAULT_CAPACITY, minCapacity)];
    }
}
```

`oldCapacity >> 1` 就是 **1.5 倍**（`newLength = old + max(need, old>>1)`）。扩容序列：`10 → 15 → 22 → 33 → 49 → 73 …`

**为什么是 1.5 而不是 2？**

- 2 倍扩容浪费内存更多：`n` 次 add 的峰值内存约为元素数的 2 倍，1.5 倍只有 1.5 倍左右
- 1.5 倍能**复用之前释放的数组空间**（多次扩容的总和是等比级数，1.5 的级数和 < 2 的空间块更可能落入被回收的空洞）
- 但代价是扩容更频繁（哈希表用 2 倍是因为下标要用 `(n-1) & hash`，必须 2 的幂）

**溢出保护**（`ArraysSupport.newLength`，ArraysSupport.java:897）：

```java
int prefLength = oldLength + Math.max(minGrowth, prefGrowth); // 可能溢出
if (0 < prefLength && prefLength <= SOFT_MAX_ARRAY_LENGTH) return prefLength;
else return hugeLength(oldLength, minGrowth);
```

`SOFT_MAX_ARRAY_LENGTH = Integer.MAX_VALUE - 8`（留 8 个字的数组头开销），超过就退化成"最小增长"，再超就抛 `OutOfMemoryError`。

**`grow` 的开销**：`Arrays.copyOf` 会创建一个新数组并把旧元素**逐个引用复制**过去（`System.arraycopy`，native 的 memmove），所以扩容是 O(n)。**均摊下来尾部 add 仍是 O(1)**——因为每 n 次 add 才可能有一次 O(n) 扩容。

## 五、add(int, E)：插入要搬元素

```java
public void add(int index, E element) {   // :510
    rangeCheckForAdd(index);
    modCount++;
    final int s;
    Object[] elementData;
    if ((s = size) == (elementData = this.elementData).length)
        elementData = grow();
    System.arraycopy(elementData, index, elementData, index + 1, s - index);
    elementData[index] = element;
    size = s + 1;
}
```

`System.arraycopy(es, index, es, index+1, s-index)` 把 `[index, size)` 整体**右移一格**，再从 index 写入。时间复杂度 **O(n - index)**：插在尾部是 O(1)（不用搬），插在 0 位置是 O(n)。这也是"ArrayList 不适合频繁头部插入"的原因。

## 六、remove：左移 + 置 null

```java
public E remove(int index) {              // :551
    Objects.checkIndex(index, size);
    final Object[] es = elementData;
    E oldValue = (E) es[index];
    fastRemove(es, index);
    return oldValue;
}

private void fastRemove(Object[] es, int i) {   // :720
    modCount++;
    final int newSize;
    if ((newSize = size - 1) > i)
        System.arraycopy(es, i + 1, es, i, newSize - i);  // 左移一格
    es[size = newSize] = null;                        // 末尾置 null
}
```

**`es[size] = null` 这行不能少**：否则数组末尾还持有对象引用，GC 无法回收这个对象，等于内存泄漏（这就是"对象已从 List 删除但没被回收"的经典来源）。

`clear()`（:732）同理：只是把前 size 个位置全部置 null，**`size = 0` 但容量不变**——所以 `clear()` 之后重新 add 不会触发扩容。想真正释放容量要调 `trimToSize()`。

## 七、get：O(1)

```java
public E get(int index) {                // :427
    Objects.checkIndex(index, size);     // JDK 9+ 统一用这个
    return elementAt(elementData, index);
}
```

一次数组下标访问，天然 O(1)；`ArrayList` 还实现了 `RandomAccess` 这个**空标记接口**，`Collections.binarySearch`、`Collections.shuffle` 会据此选择下标遍历而不是迭代器遍历。

## 八、modCount 与 fail-fast（面试高频）

`modCount` 定义在 `AbstractList` 里，记录**结构性修改**的次数（add/remove/clear/ensureCapacity 都 `++`）。

```java
private class Itr implements Iterator<E> {        // :1036
    int cursor;
    int lastRet = -1;
    int expectedModCount = modCount;              // 创建迭代器时"记住"当前版本号

    public E next() {
        checkForComodification();                 // :1050
        ...
    }
    final void checkForComodification() {         // :1094
        if (modCount != expectedModCount)
            throw new ConcurrentModificationException();
    }
}
```

- `for (E e : list) list.remove(e);` 会抛 `ConcurrentModificationException`：`next()` 发现版本号对不上
- 正确姿势：`Iterator.remove()`（它会同步 `expectedModCount = modCount`，:1070），或 `list.removeIf(...)`
- **`ensureCapacity` 也会 `modCount++`**（:220），所以迭代中调它同样会 CME
- 注意：`modCount` 是**非 volatile 的普通 int**，它的作用不是线程安全，而是"检测不一致"；多线程下它本身还可能读到脏值——所以 **fail-fast 是尽力而为（best-effort），不是保证**

> 顺带一个新版本彩蛋：JDK 23 的 `removeIf` 用位图 `deathRow` 标记要删的元素、两趟扫描完成删除（:1751-1782），避免每次 `remove` 都搬一次数组。

## 九、为什么 ArrayList 不是线程安全的

`add(E)` 一行代码在字节码层面是「读 size → 写 elementData[size] → size+1」多个步骤：

- 两个线程同时 add 到同一个下标 → **后写的覆盖先写的，丢数据**
- 两个线程同时判断"满了没有" → 可能都以为没满，**数组越界**或一个数组被另一个覆盖
- `size` 的 `+1` 不是原子操作 → 最终 size 偏小，元素个数对不上

所以并发写要用 `CopyOnWriteArrayList`（读多写少、写时复制整数组，弱一致）或 `Collections.synchronizedList`（全部加锁）。**注意 `Collections.synchronizedList` 返回的列表遍历时仍需手动 `synchronized (list)`**，因为它的迭代器不加锁。

## 十、ArrayList vs LinkedList

| 维度 | ArrayList | LinkedList |
|---|---|---|
| 底层 | 动态数组 | 双向链表（`Node{prev,item,next}`） |
| `get(i)` | **O(1)** | O(n)（从头/尾走到 i） |
| 尾部 `add` | 均摊 O(1)，偶尔 O(n) 扩容 | O(1)（但要改两个指针） |
| 头部 `add` | O(n) 搬移 | **O(1)** |
| 中间插入 | O(n) | 找到位置 O(n) + 改指针 O(1) |
| 内存 | 紧凑（连续对象引用数组） | 每元素多两个引用（prev/next），**每个 Node 对象头** |
| 缓存友好度 | **高**（连续内存，CPU 预取命中） | 低（指针跳转，cache miss） |
| 随机访问接口 | 实现 `RandomAccess` | 不实现 |

**结论**：绝大多数场景用 `ArrayList`；LinkedList 只有在"频繁头尾增删且几乎不按下标访问"时才可能赢，而且 JVM 上 `LinkedList` 因为缓存不友好，实测常常还更慢。这也是面试官爱问"那 LinkedList 真有优势吗"的原因。

## 十一、使用建议

- **能预估数量就用 `new ArrayList<>(预计大小)`**，避免 10→15→22 一路扩容复制
- 大量 `add` 之前可 `ensureCapacity(n)`
- 删除用 `iterator.remove()` / `removeIf()`，别在 for-each 里 `remove`
- 返回列表时用 `Collections.unmodifiableList` 或 `List.copyOf` 防止外部修改
- `subList` 是**视图**不是拷贝：改子列表会改原列表；原列表结构性修改后子列表操作抛 CME
- `Arrays.asList(...)` 返回定长视图（不能 add/remove），要可变得 `new ArrayList<>(Arrays.asList(...))`

## 十二、自测 6 题

1. `new ArrayList<>()` 后立刻 `size()` 是 0，`elementData` 容量是多少？第一次 add 后容量是多少？
2. 扩容为什么是 1.5 倍？为什么 HashMap 是 2 倍而 ArrayList 不是？
3. `clear()` 之后容量会变吗？想释放内存怎么办？
4. `remove(int)` 里 `es[size] = null` 这行删掉会怎样？
5. 为什么 `for (E e : list) list.remove(e);` 会抛 CME？`iterator.remove()` 为什么不会？
6. ArrayList 线程不安全具体体现在哪几行代码？

### 答案

1. 容量 0（指向 `DEFAULTCAPACITY_EMPTY_ELEMENTDATA`，没分配）；第一次 add 后是 **10**。
2. 1.5 倍是内存利用率与扩容频率的折中，且更容易复用已释放的数组空间；HashMap 用 2 倍是因为下标计算依赖 `(n-1) & hash`，容量必须是 2 的幂。
3. 不变，`clear()` 只把前 size 个元素置 null；`trimToSize()` 才会把 `elementData` 缩到 size 大小。
4. 数组末尾仍持有该对象的引用，GC 无法回收 → 内存泄漏；同时 `size` 语义与数组内容不一致。
5. `modCount` 在 remove 时自增，而迭代器创建时记录的 `expectedModCount` 没变，`next()` 里的 `checkForComodification()` 发现不一致就抛；`iterator.remove()` 内部会 `expectedModCount = modCount`。
6. `add(E)` 的 `modCount++`、`elementData[s] = e`、`size = s + 1` 三步非原子；`grow()` 期间新数组替换也可能被并发覆盖。
