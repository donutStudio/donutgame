package com.donutsforlife11.microrush;

import java.util.*;

final class InventoryLayoutPlan {
    record Start(int id, int slot, int count, int maxStack) { }
    public record Placement(int id, int slot, int count) { }

    static List<Placement> restore(List<Start> kit, Placement[] saved) {
        Map<Integer, Start> sources = new LinkedHashMap<>();
        Map<Integer, Integer> left = new HashMap<>();
        Map<Integer, Placement> result = new LinkedHashMap<>();
        for (Start item : kit) { sources.put(item.id, item); left.put(item.id, item.count); }
        for (Placement wanted : saved) {
            if (wanted == null || wanted.slot < 0 || wanted.slot > 40 || wanted.count <= 0 || result.containsKey(wanted.slot)) continue;
            Start source = sources.get(wanted.id);
            if (source == null) continue;
            int count = Math.min(Math.min(wanted.count, source.maxStack), left.get(wanted.id));
            if (count <= 0) continue;
            result.put(wanted.slot,new Placement(wanted.id,wanted.slot,count));
            left.merge(wanted.id,-count,Integer::sum);
        }
        for (Start item : kit) {
            while (left.get(item.id) > 0) {
                int slot = result.containsKey(item.slot) ? emptySlot(result) : item.slot;
                // RODO: Config changes can make old split layouts too fragmented. Fall back to the current kit instead of losing items.
                if (slot < 0) return kit.stream().map(start -> new Placement(start.id,start.slot,start.count)).toList();
                int count = Math.min(left.get(item.id),item.maxStack);
                result.put(slot,new Placement(item.id,slot,count));
                left.merge(item.id,-count,Integer::sum);
            }
        }
        return List.copyOf(result.values());
    }

    private static int emptySlot(Map<Integer, Placement> items) {
        for (int slot = 0; slot < 36; slot++) if (!items.containsKey(slot)) return slot;
        return -1;
    }
}
