package com.anime.magic;

import com.anime.magic.animation.AnimationRegistry;
import com.anime.magic.commands.*;
import com.anime.magic.controls.*;
import com.anime.magic.core.MessageService;
import com.anime.magic.core.ModuleManager;
import com.anime.magic.core.VersionManager;
import com.anime.magic.effects.ParticleEngine;
import com.anime.magic.gui.GUIManager;
import com.anime.magic.listeners.CombatListener;
import com.anime.magic.listeners.GUIListener;
import com.anime.magic.listeners.PlayerListener;
import com.anime.magic.mana.ManaManager;
import com.anime.magic.mana.ManaRegenTask;
import com.anime.magic.metrics.Metrics;
import com.anime.magic.minigame.ArenaManager;
import com.anime.magic.api.SpellRegistry;
import com.anime.magic.models.ModelRegistry;
import com.anime.magic.schools.mushoku.MushokuSchool;
import com.anime.magic.schools.naruto.NarutoSchool;
import com.anime.magic.schools.onepiece.OnePieceSchool;
import com.anime.magic.schools.tensura.TensuraSchool;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import java.util.logging.Level;

/**
 * Main entry point for the AnimeMagic plugin. Split into independent modules
 * (mana, effects, gui, arena, schools, models, animations, controls) coordinated
 * by a ModuleManager.
 */
public final class AnimeMagicPlugin extends JavaPlugin {
    private static AnimeMagicPlugin instance;

    private VersionManager versionManager;
    private ModuleManager moduleManager;
    private MessageService messages;
    private ManaManager manaManager;
    private ManaRegenTask manaRegenTask;
    private ParticleEngine particleEngine;
    private GUIManager guiManager;
    private ArenaManager arenaManager;
    private SpellRegistry spellRegistry;
    private Metrics metrics;
    private BukkitTask autoSaveTask;

    private ModelRegistry modelRegistry;
    private AnimationRegistry animationRegistry;
    private ControlManager controlManager;
    private DefaultBindings defaultBindings;
    private BukkitTask controlTickerTask;
    private DoubleJumpCastControl doubleJumpControl;
    // v5 cinematic systems
    private com.anime.magic.cinematic.DestructionSystem destructionSystem;
    private com.anime.magic.cinematic.ScreenShakeSystem screenShakeSystem;
    private com.anime.magic.cinematic.ImpactFrameSystem impactFrameSystem;
    private com.anime.magic.cinematic.CinematicEffects cinematicEffects;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        saveResource("messages.yml", false);
        saveResource("gui_textures.yml", false);

        this.versionManager = new VersionManager(getServer());
        getLogger().info("Detected server version: " + versionManager.getServerVersion()
                + " (api=" + versionManager.getApiVersion() + ", folia=" + versionManager.isFolia() + ")");
        if (!versionManager.isAtLeast_1_20()) {
            getLogger().warning("Custom models (ItemDisplay) require Paper 1.19.4+; some features will be no-ops on this version.");
        }

        this.messages = new MessageService(this);
        this.moduleManager = new ModuleManager(this);

        this.particleEngine = new ParticleEngine(this);
        this.guiManager = new GUIManager(this);
        this.guiManager.load();

        this.manaManager = new ManaManager(this);
        this.manaManager.loadAll();
        this.manaRegenTask = new ManaRegenTask(this);
        this.manaRegenTask.runTaskTimer(this, 20L, 20L);

        this.modelRegistry = new ModelRegistry(this);
        this.modelRegistry.reload();
        this.animationRegistry = new AnimationRegistry(this);
        this.animationRegistry.reload();

        this.spellRegistry = new SpellRegistry();
        registerSchools();

        this.arenaManager = new ArenaManager(this);
        this.arenaManager.load();

        this.controlManager = new ControlManager(this);
        this.controlManager.load();
        this.defaultBindings = new DefaultBindings(this);
        registerControls();
        this.controlTickerTask = Bukkit.getScheduler().runTaskTimer(this, () -> controlManager.tickAll(), 1L, 1L);

        // v5 cinematic systems
        this.destructionSystem = new com.anime.magic.cinematic.DestructionSystem(this);
        this.screenShakeSystem = new com.anime.magic.cinematic.ScreenShakeSystem(this);
        this.impactFrameSystem = new com.anime.magic.cinematic.ImpactFrameSystem(this);
        this.cinematicEffects = new com.anime.magic.cinematic.CinematicEffects(this);

        Bukkit.getPluginManager().registerEvents(new PlayerListener(this), this);
        Bukkit.getPluginManager().registerEvents(new CombatListener(this), this);
        Bukkit.getPluginManager().registerEvents(new GUIListener(this), this);
        Bukkit.getPluginManager().registerEvents(new ControlListener(this), this);
        Bukkit.getPluginManager().registerEvents(arenaManager, this);
        // DoubleJumpCastControl needs to listen for PlayerToggleFlightEvent
        if (doubleJumpControl != null) {
            Bukkit.getPluginManager().registerEvents(doubleJumpControl, this);
        }

        getCommand("magic").setExecutor(new MagicCommand(this));
        getCommand("magic").setTabCompleter(new MagicCommand(this));
        getCommand("spell").setExecutor(new SpellCommand(this));
        getCommand("spell").setTabCompleter(new SpellCommand(this));
        getCommand("mana").setExecutor(new ManaCommand(this));
        getCommand("mana").setTabCompleter(new ManaCommand(this));
        getCommand("arena").setExecutor(new ArenaCommand(this));
        getCommand("arena").setTabCompleter(new ArenaCommand(this));
        getCommand("spellbook").setExecutor(new SpellbookCommand(this));
        getCommand("bind").setExecutor(new BindCommand(this));
        getCommand("bind").setTabCompleter(new BindCommand(this));
        getCommand("school").setExecutor(new SchoolCommand(this));
        getCommand("school").setTabCompleter(new SchoolCommand(this));

        if (getConfig().getBoolean("metrics.enabled", true)) {
            int id = getConfig().getInt("metrics.plugin-id", 0);
            if (id > 0) {
                this.metrics = new Metrics(this, id);
                metrics.addCustomChart(new Metrics.SimplePie("school_naruto",
                        () -> String.valueOf(getConfig().getBoolean("schools.naruto.enabled"))));
                metrics.addCustomChart(new Metrics.SimplePie("school_tensura",
                        () -> String.valueOf(getConfig().getBoolean("schools.tensura.enabled"))));
                metrics.addCustomChart(new Metrics.SimplePie("school_mushoku",
                        () -> String.valueOf(getConfig().getBoolean("schools.mushoku.enabled"))));
                metrics.addCustomChart(new Metrics.SimplePie("school_onepiece",
                        () -> String.valueOf(getConfig().getBoolean("schools.onepiece.enabled"))));
                metrics.addCustomChart(new Metrics.SingleLineChart("learned_spells_total",
                        () -> spellRegistry != null ? spellRegistry.size() : 0));
                metrics.addCustomChart(new Metrics.SingleLineChart("custom_models_loaded",
                        () -> modelRegistry != null ? modelRegistry.size() : 0));
                metrics.addCustomChart(new Metrics.SingleLineChart("animations_loaded",
                        () -> animationRegistry != null ? animationRegistry.size() : 0));
            } else {
                getLogger().info("Metrics enabled but plugin-id is 0 — register at bstats.org and set the id.");
            }
        }

        int saveInterval = getConfig().getInt("storage.auto-save-seconds", 300);
        this.autoSaveTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            try {
                manaManager.saveAll();
                arenaManager.save();
                controlManager.save();
            } catch (Exception ex) {
                getLogger().log(Level.WARNING, "Auto-save failed", ex);
            }
        }, saveInterval * 20L, saveInterval * 20L);

        getLogger().info("AnimeMagic enabled — " + spellRegistry.size() + " spells, "
                + modelRegistry.size() + " models, " + animationRegistry.size() + " animations registered.");
    }

    private void registerSchools() {
        if (getConfig().getBoolean("schools.naruto.enabled", true)) new NarutoSchool(this).register(spellRegistry);
        if (getConfig().getBoolean("schools.tensura.enabled", true)) new TensuraSchool(this).register(spellRegistry);
        if (getConfig().getBoolean("schools.mushoku.enabled", true)) new MushokuSchool(this).register(spellRegistry);
        if (getConfig().getBoolean("schools.onepiece.enabled", true)) new OnePieceSchool(this).register(spellRegistry);
    }

    private void registerControls() {
        controlManager.register(new HotbarControl(this));
        controlManager.register(new SpellWheelControl(this));
        controlManager.register(new SneakCastControl(this));
        controlManager.register(new ComboControl(this));
        controlManager.register(new CastBarControl(this));
        controlManager.register(new LookCastControl(this));
        this.doubleJumpControl = new DoubleJumpCastControl(this);
        controlManager.register(doubleJumpControl);
    }

    @Override
    public void onDisable() {
        if (autoSaveTask != null) autoSaveTask.cancel();
        if (controlTickerTask != null) controlTickerTask.cancel();
        if (manaRegenTask != null) manaRegenTask.cancel();
        if (particleEngine != null) particleEngine.shutdown();
        if (guiManager != null) guiManager.closeAll();
        if (arenaManager != null) { arenaManager.stopAll(); arenaManager.save(); }
        if (manaManager != null) manaManager.saveAll();
        if (controlManager != null) controlManager.save();
        getLogger().info("AnimeMagic disabled.");
    }

    public void reload(CommandSender sender) {
        long start = System.currentTimeMillis();
        reloadConfig();
        messages.reload();
        spellRegistry.clear();
        registerSchools();
        if (modelRegistry != null) modelRegistry.reload();
        if (animationRegistry != null) animationRegistry.reload();
        long took = System.currentTimeMillis() - start;
        messages.send(sender, "reloaded", "%ms%", String.valueOf(took));
    }

    public static AnimeMagicPlugin getInstance() { return instance; }
    public VersionManager getVersionManager() { return versionManager; }
    public ModuleManager getModuleManager() { return moduleManager; }
    public MessageService getMessages() { return messages; }
    public ManaManager getManaManager() { return manaManager; }
    public ParticleEngine getParticleEngine() { return particleEngine; }
    public GUIManager getGuiManager() { return guiManager; }
    public ArenaManager getArenaManager() { return arenaManager; }
    public SpellRegistry getSpellRegistry() { return spellRegistry; }
    public ModelRegistry getModelRegistry() { return modelRegistry; }
    public AnimationRegistry getAnimationRegistry() { return animationRegistry; }
    public ControlManager getControlManager() { return controlManager; }
    public DefaultBindings getDefaultBindings() { return defaultBindings; }
    public com.anime.magic.cinematic.DestructionSystem getDestructionSystem() { return destructionSystem; }
    public com.anime.magic.cinematic.ScreenShakeSystem getScreenShakeSystem() { return screenShakeSystem; }
    public com.anime.magic.cinematic.ImpactFrameSystem getImpactFrameSystem() { return impactFrameSystem; }
    public com.anime.magic.cinematic.CinematicEffects getCinematicEffects() { return cinematicEffects; }
}
