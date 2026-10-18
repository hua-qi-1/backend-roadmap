import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * LeetCode 1. 两数之和
 * https://leetcode.cn/problems/two-sum/
 *
 * 提交到 LeetCode 时只交 class Solution 那一段；下面的 main 只是本地自测用。
 */
class Solution {

    /**
     * 哈希表一次遍历：O(n) 时间，O(n) 空间 —— 面试标准答案。
     *
     * 核心：边遍历边记录「见过的数值 -> 它的下标」，每到一个新元素就先查
     * 表里有没有 target - nums[i]，有就说明配对成功。
     *
     * 为什么必须先查后插：先插再查时会查到自己（nums=[3,3] 会返回 [0,0]）。
     */
    public int[] twoSum(int[] nums, int target) {
        Map<Integer, Integer> seen = new HashMap<>();
        for (int i = 0; i < nums.length; i++) {
            int need = target - nums[i];
            if (seen.containsKey(need)) {
                return new int[]{seen.get(need), i};
            }
            seen.put(nums[i], i);
        }
        return new int[]{};   // 题目保证有解，理论上到不了这里
    }

    /**
     * 暴力枚举：O(n^2) 时间，O(1) 空间 —— 用来对照、验证哈希版结果是否一致。
     */
    public int[] twoSumBruteForce(int[] nums, int target) {
        for (int i = 0; i < nums.length - 1; i++) {
            for (int j = i + 1; j < nums.length; j++) {
                if (nums[i] + nums[j] == target) {
                    return new int[]{i, j};
                }
            }
        }
        return new int[]{};
    }
}

public class TwoSum {

    public static void main(String[] args) {
        Solution s = new Solution();

        int[][] cases = {{2, 7, 11, 15}, {3, 2, 4}, {3, 3}, {-1, -2, -3, -4, -5}};
        int[] targets = {9, 6, 6, -8};

        for (int k = 0; k < cases.length; k++) {
            int[] hash = s.twoSum(cases[k], targets[k]);
            int[] brute = s.twoSumBruteForce(cases[k], targets[k]);
            System.out.printf(
                    "nums=%-22s target=%-3d hash=%-9s brute=%-9s %s%n",
                    Arrays.toString(cases[k]),
                    targets[k],
                    Arrays.toString(hash),
                    Arrays.toString(brute),
                    Arrays.equals(hash, brute) ? "OK" : "MISMATCH");
        }
    }
}
