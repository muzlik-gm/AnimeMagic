package com.anime.magic.core;

import com.anime.magic.AnimeMagicPlugin;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads and caches messages.yml, performing placeholder substitution on every send().
 * Placeholders are written as %name% in the YAML file and passed as alternating key/value pairs.
 */
public final class MessageService {
    private final AnimeMagicPlugin plugin;
    private final File file;
    private YamlConfiguration yaml;
    private String prefix = "";

    public MessageService(AnimeMagicPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "messages.yml");
        reload();
    }

    public void reload() {
        if (!file.exists()) plugin.saveResource("messages.yml", false);
        this.yaml = YamlConfiguration.loadConfiguration(file);
        try (InputStream in = plugin.getResource("messages.yml");
             InputStreamReader reader = in == null ? null : new InputStreamReader(in, StandardCharsets.UTF_8)) {
            if (reader != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(reader);
                yaml.setDefaults(defaults);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Could not read default messages.yml: " + e.getMessage());
        }
        this.prefix = translate(yaml.getString("prefix", ""));
    }

    public String raw(String key) { return translate(yaml.getString(key, key)); }

    public String format(String key, Object... kvPairs) {
        return applyPlaceholders(raw(key), kvPairs);
    }

    public void send(CommandSender target, String key, Object... kvPairs) {
        if (target == null) return;
        String message = format(key, kvPairs);
        if (message.isEmpty()) return;
        target.sendMessage(prefix + message);
    }

    public void sendNoPrefix(CommandSender target, String key, Object... kvPairs) {
        if (target == null) return;
        target.sendMessage(format(key, kvPairs));
    }

    public static String translate(String s) { return s == null ? "" : s.replace('&', '\u00A7'); }

    private static String applyPlaceholders(String s, Object... kvPairs) {
        if (kvPairs == null || kvPairs.length == 0) return s;
        Map<String, String> map = new HashMap<>(kvPairs.length / 2 + 1);
        for (int i = 0; i + 1 < kvPairs.length; i += 2) {
            map.put(String.valueOf(kvPairs[i]), String.valueOf(kvPairs[i + 1]));
        }
        for (Map.Entry<String, String> e : map.entrySet()) s = s.replace(e.getKey(), e.getValue());
        return s;
    }

    public YamlConfiguration getYaml() { return yaml; }
}
