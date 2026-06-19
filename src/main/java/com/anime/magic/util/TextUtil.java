package com.anime.magic.util;

import com.anime.magic.core.MessageService;
import net.md_5.bungee.api.ChatColor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Text helpers — color translation, hex colors, formatting. */
public final class TextUtil {
    private static final Pattern HEX = Pattern.compile("&?#([0-9a-fA-F]{6})");
    private TextUtil() {}

    public static String color(String s) {
        if (s == null) return "";
        Matcher m = HEX.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String hex = m.group(1);
            m.appendReplacement(sb, ChatColor.of("#" + hex).toString());
        }
        m.appendTail(sb);
        return MessageService.translate(sb.toString());
    }

    public static String[] color(String[] lines) {
        String[] out = new String[lines.length];
        for (int i = 0; i < lines.length; i++) out[i] = color(lines[i]);
        return out;
    }

    public static String titleCase(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
