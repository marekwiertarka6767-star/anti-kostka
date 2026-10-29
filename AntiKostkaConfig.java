package dev.antikostka.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.antikostka.match.Fingerprint;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/** Settings and saved layout, stored in config/anti_kostka.json. */
public final class AntiKostkaConfig {
    public enum MatchMode { BASIC, ADVANCED }

    public static final int HOTBAR_SIZE = 9;

    private static final Logger LOGGER = LoggerFactory.getLogger("anti_kostka");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static AntiKostkaConfig instance;

    public boolean enabled = true;
    public boolean showIndicator = true;
    public MatchMode matchMode = MatchMode.BASIC;
    public boolean matchEnchantments = true;
    public boolean matchCustomName = true;
    public boolean matchDurability = false;
    public boolean matchAllComponents = false;
    /** Ticks between two restore clicks. */
    public int restoreDelayTicks = 4;
    /** Ticks to wait after a screen closes before restoring (time to press Save after rearranging). */
    public int graceTicks = 40;
    /** One entry per hotbar slot; null means "no requirement / empty". */
    public Fingerprint[] layout = new Fingerprint[HOTBAR_SIZE];

    /** Incremented on every change so the restorer re-evaluates. Not persisted. */
    public int revision = 0;

    public static AntiKostkaConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    public boolean hasLayout() {
        for (Fingerprint f : layout) {
            if (f != null) {
                return true;
            }
        }
        return false;
    }

    public void clearLayout() {
        layout = new Fingerprint[HOTBAR_SIZE];
        save();
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("anti_kostka.json");
    }

    // ---------------------------------------------------------------- load

    private static AntiKostkaConfig load() {
        AntiKostkaConfig cfg = new AntiKostkaConfig();
        Path file = path();
        if (!Files.exists(file)) {
            return cfg;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (root == null || !root.isJsonObject()) {
                throw new IllegalStateException("root is not a JSON object");
            }
            JsonObject o = root.getAsJsonObject();
            cfg.enabled = readBool(o, "enabled", cfg.enabled);
            cfg.showIndicator = readBool(o, "showIndicator", cfg.showIndicator);
            cfg.matchEnchantments = readBool(o, "matchEnchantments", cfg.matchEnchantments);
            cfg.matchCustomName = readBool(o, "matchCustomName", cfg.matchCustomName);
            cfg.matchDurability = readBool(o, "matchDurability", cfg.matchDurability);
            cfg.matchAllComponents = readBool(o, "matchAllComponents", cfg.matchAllComponents);
            cfg.restoreDelayTicks = clamp(readInt(o, "restoreDelayTicks", cfg.restoreDelayTicks), 2, 40);
            cfg.graceTicks = clamp(readInt(o, "graceTicks", cfg.graceTicks), 0, 400);
            if (o.has("matchMode") && o.get("matchMode").isJsonPrimitive()) {
                try {
                    cfg.matchMode = MatchMode.valueOf(o.get("matchMode").getAsString().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ignored) {
                    // keep default
                }
            }
            if (o.has("layout") && o.get("layout").isJsonArray()) {
                JsonArray array = o.getAsJsonArray("layout");
                for (int i = 0; i < HOTBAR_SIZE && i < array.size(); i++) {
                    JsonElement e = array.get(i);
                    try {
                        cfg.layout[i] = e.isJsonObject() ? Fingerprint.fromJson(e.getAsJsonObject()) : null;
                    } catch (RuntimeException ex) {
                        cfg.layout[i] = null; // one bad slot must not discard the rest
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Could not read {}, starting with defaults", file, e);
            try {
                Files.move(file, file.resolveSibling("anti_kostka.json.invalid"), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException moveError) {
                LOGGER.warn("Could not back up invalid config", moveError);
            }
            return new AntiKostkaConfig();
        }
        return cfg;
    }

    private static boolean readBool(JsonObject o, String key, boolean def) {
        return o.has(key) && o.get(key).isJsonPrimitive() && o.get(key).getAsJsonPrimitive().isBoolean()
                ? o.get(key).getAsBoolean() : def;
    }

    private static int readInt(JsonObject o, String key, int def) {
        try {
            return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsInt() : def;
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    // ---------------------------------------------------------------- save

    public void save() {
        revision++;
        JsonObject o = new JsonObject();
        o.addProperty("enabled", enabled);
        o.addProperty("showIndicator", showIndicator);
        o.addProperty("matchMode", matchMode.name());
        o.addProperty("matchEnchantments", matchEnchantments);
        o.addProperty("matchCustomName", matchCustomName);
        o.addProperty("matchDurability", matchDurability);
        o.addProperty("matchAllComponents", matchAllComponents);
        o.addProperty("restoreDelayTicks", restoreDelayTicks);
        o.addProperty("graceTicks", graceTicks);
        JsonArray array = new JsonArray();
        for (Fingerprint f : layout) {
            array.add(f == null ? JsonNull.INSTANCE : f.toJson());
        }
        o.add("layout", array);

        Path file = path();
        Path tmp = file.resolveSibling("anti_kostka.json.tmp");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(tmp, GSON.toJson(o), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LOGGER.error("Could not write {}", file, e);
        }
    }
}
