package com.controlbro.levely.manager;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

public class LootTable<T> {
    private final Map<T, Double> weights;

    public LootTable() {
        this.weights = new LinkedHashMap<>();
    }

    public void add(T entry, double weight) {
        if (entry == null || weight <= 0) {
            return;
        }
        weights.put(entry, weight);
    }

    public Optional<T> roll(Random random) {
        if (weights.isEmpty()) {
            return Optional.empty();
        }
        double total = weights.values().stream().mapToDouble(Double::doubleValue).sum();
        if (total <= 0) {
            return Optional.empty();
        }
        double roll = random.nextDouble() * total;
        double running = 0;
        for (Map.Entry<T, Double> entry : weights.entrySet()) {
            running += entry.getValue();
            if (roll <= running) {
                return Optional.of(entry.getKey());
            }
        }
        return Optional.of(weights.keySet().iterator().next());
    }

    public boolean isEmpty() {
        return weights.isEmpty();
    }
}
