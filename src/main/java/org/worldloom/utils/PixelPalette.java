package org.worldloom.utils;

import java.util.*;

/** 无GL依赖的像素图减色和索引匹配；不持有纹理、实体或玩法状态。 */
public final class PixelPalette {
    private final int[] colors, indices;

    /** 保存本次提取的独立数组，查询方不能修改内部结果。 */
    private PixelPalette(int[] colors, int[] indices) { this.colors = colors; this.indices = indices; }

    /**
     * 从RGBA8888原生像素提取色板；透明像素记为-1，其余颜色统一为不透明。
     * 先按step量化RGB，再按像素权重中位切分到maxColors以内，最后映射最近RGB色。
     * 输入最多1048576像素，step为1～255，maxColors为1～256，alphaThreshold为1～255。
     * 不缩放、不读写资源；相同输入和参数在不同机器上产生相同顺序及结果。
     */
    public static PixelPalette extract(int[] rgba, int step, int maxColors, int alphaThreshold) {
        if (rgba == null || rgba.length == 0 || rgba.length > 1_048_576 || step < 1 || step > 255
            || maxColors < 1 || maxColors > 256 || alphaThreshold < 1 || alphaThreshold > 255)
            throw new IllegalArgumentException("Invalid pixel palette input");
        Map<Integer, Integer> histogram = new TreeMap<>(Integer::compareUnsigned);
        for (int color : rgba) if ((color & 255) >= alphaThreshold) histogram.merge(quantize(color, step), 1, Integer::sum);
        if (histogram.isEmpty()) {
            int[] empty = new int[rgba.length]; Arrays.fill(empty, -1); return new PixelPalette(new int[0], empty);
        }
        List<List<Integer>> boxes = new ArrayList<>(); boxes.add(new ArrayList<>(histogram.keySet()));
        while (boxes.size() < maxColors) {
            int selected = -1, bestRange = -1;
            for (int i = 0; i < boxes.size(); i++) {
                if (boxes.get(i).size() < 2) continue;
                int range = range(boxes.get(i), channel(boxes.get(i)));
                if (range > bestRange) { bestRange = range; selected = i; }
            }
            if (selected < 0) break;
            List<Integer> box = boxes.remove(selected); int channel = channel(box);
            box.sort(Comparator.comparingInt((Integer color) -> color >>> channel & 255).thenComparing(Integer::compareUnsigned));
            int total = 0; for (int color : box) total += histogram.get(color);
            int accumulated = 0, split = 0;
            do { accumulated += histogram.get(box.get(split++)); } while (split < box.size() - 1 && accumulated * 2 < total);
            boxes.add(new ArrayList<>(box.subList(0, split))); boxes.add(new ArrayList<>(box.subList(split, box.size())));
        }
        TreeSet<Integer> palette = new TreeSet<>(Integer::compareUnsigned);
        for (List<Integer> box : boxes) {
            long red = 0, green = 0, blue = 0, weight = 0;
            for (int color : box) {
                int count = histogram.get(color); weight += count;
                red += (long)(color >>> 24) * count; green += (long)(color >>> 16 & 255) * count; blue += (long)(color >>> 8 & 255) * count;
            }
            palette.add((int)((red + weight / 2) / weight) << 24 | (int)((green + weight / 2) / weight) << 16
                | (int)((blue + weight / 2) / weight) << 8 | 255);
        }
        int[] candidates = palette.stream().mapToInt(Integer::intValue).toArray(), indices = new int[rgba.length];
        Map<Integer, Integer> mapped = new HashMap<>(); boolean[] used = new boolean[candidates.length];
        for (int color : histogram.keySet()) {
            int nearest = 0, best = Integer.MAX_VALUE;
            for (int i = 0; i < candidates.length; i++) {
                int distance = distance(color, candidates[i]); if (distance < best) { best = distance; nearest = i; }
            }
            mapped.put(color, nearest); used[nearest] = true;
        }
        int count = 0; int[] remap = new int[candidates.length];
        for (int i = 0; i < used.length; i++) if (used[i]) remap[i] = count++;
        int[] colors = new int[count];
        for (int i = 0; i < used.length; i++) if (used[i]) colors[remap[i]] = candidates[i];
        for (int i = 0; i < rgba.length; i++) indices[i] = (rgba[i] & 255) < alphaThreshold ? -1 : remap[mapped.get(quantize(rgba[i], step))];
        return new PixelPalette(colors, indices);
    }

    /** 固定整数通道量化，避免不同平台的小数舍入影响色板。 */
    private static int quantize(int rgba, int step) {
        int color = 255;
        for (int shift : new int[] {24, 16, 8}) color |= Math.min(255, ((rgba >>> shift & 255) + step / 2) / step * step) << shift;
        return color;
    }
    /** 寻找变化最大的通道，平局按RGB顺序固定选择。 */
    private static int channel(List<Integer> box) {
        int selected = 24, best = -1;
        for (int shift : new int[] {24, 16, 8}) { int range = range(box, shift); if (range > best) { best = range; selected = shift; } }
        return selected;
    }
    /** 读取单个颜色箱在指定通道上的跨度。 */
    private static int range(List<Integer> box, int shift) {
        int min = 255, max = 0;
        for (int color : box) { int value = color >>> shift & 255; min = Math.min(min, value); max = Math.max(max, value); }
        return max - min;
    }
    /** 最近色映射使用RGB平方距离，不涉及透明度或游戏分数。 */
    private static int distance(int a, int b) {
        int r = (a >>> 24) - (b >>> 24), g = (a >>> 16 & 255) - (b >>> 16 & 255), blue = (a >>> 8 & 255) - (b >>> 8 & 255);
        return r * r + g * g + blue * blue;
    }
    /** 返回独立调色板副本；全透明输入返回空数组。 */
    public int[] colors() { return colors.clone(); }
    /** 返回按输入顺序排列的索引副本，透明位置为-1。 */
    public int[] indices() { return indices.clone(); }
    /**
     * 对比采用同一色板的两组索引，仅统计目标非透明位置的精确命中数。
     * -1代表透明或未填；其余索引必须非负。长度不一致或非法负数直接拒绝。
     * 颜色量化应在提取目标时完成，调用方不得用不同色板的索引直接比较。
     */
    public static int countMatches(int[] target, int[] actual) {
        if (target == null || actual == null || target.length != actual.length) throw new IllegalArgumentException("Mismatched indexed images");
        int correct = 0;
        for (int i = 0; i < target.length; i++) {
            if (target[i] < -1 || actual[i] < -1) throw new IllegalArgumentException("Invalid color index");
            if (target[i] >= 0 && target[i] == actual[i]) correct++;
        }
        return correct;
    }
}
