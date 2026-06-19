package com.anime.magic.metrics;

import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import java.util.Map;
import java.util.function.Supplier;

/** Thin wrapper around bStats Bukkit Metrics. */
public final class Metrics {
    private final org.bstats.bukkit.Metrics bstats;

    public Metrics(@NotNull JavaPlugin plugin, int pluginId) {
        this.bstats = new org.bstats.bukkit.Metrics(plugin, pluginId);
    }

    public void addCustomChart(org.bstats.charts.CustomChart chart) { bstats.addCustomChart(chart); }

    public static final class SimplePie extends org.bstats.charts.SimplePie {
        public SimplePie(String chartId, Supplier<String> supplier) { super(chartId, supplier::get); }
    }

    public static final class SingleLineChart extends org.bstats.charts.SingleLineChart {
        public SingleLineChart(String chartId, Supplier<Integer> supplier) { super(chartId, supplier::get); }
    }

    public static final class AdvancedBarChart extends org.bstats.charts.AdvancedBarChart {
        public AdvancedBarChart(String chartId, Supplier<Map<String, int[]>> supplier) { super(chartId, () -> supplier.get()); }
    }
}
