package com.anime.magic.core;

import org.bukkit.Server;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects the running server's Minecraft version once at startup. Used by other modules
 * to branch on capabilities (e.g. ItemDisplay requires Paper 1.19.4+).
 */
public final class VersionManager {
    private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d+)\\.(\\d+)(?:\\.(\\d+))?");

    private final String serverVersion;
    private final int major, minor, patch;
    private final boolean folia;

    public VersionManager(Server server) {
        this.serverVersion = server.getBukkitVersion();
        Matcher m = VERSION_PATTERN.matcher(serverVersion);
        if (m.find()) {
            this.major = Integer.parseInt(m.group(1));
            this.minor = Integer.parseInt(m.group(2));
            this.patch = m.group(3) != null ? Integer.parseInt(m.group(3)) : 0;
        } else {
            this.major = 1; this.minor = 21; this.patch = 0;
        }
        this.folia = checkFolia();
    }

    private boolean checkFolia() {
        try { Class.forName("io.papermc.paper.threadedregions.RegionizedServer"); return true; }
        catch (ClassNotFoundException e) { return false; }
    }

    public String getServerVersion() { return serverVersion; }
    public String getApiVersion() { return serverVersion; }
    public boolean isFolia() { return folia; }
    public boolean isAtLeast_1_20() { return atLeast(1, 20, 0); }
    public boolean isAtLeast_1_21() { return atLeast(1, 21, 0); }
    public boolean atLeast(int maj, int min, int pat) {
        if (major != maj) return major > maj;
        if (minor != min) return minor > min;
        return patch >= pat;
    }

    @Override public String toString() {
        return major + "." + minor + "." + patch + (folia ? " (Folia)" : "");
    }
}
