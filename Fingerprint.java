package dev.antikostka.match;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.ComponentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Describes the identity of an item as saved in the layout.
 * <p>
 * The stack COUNT is deliberately not part of this class. All facets are always captured,
 * and the configuration decides at match time which of them are compared. That way
 * changing the matching mode never requires re-saving the layout.
 */
public final class Fingerprint {
    /** Registry id, e.g. "minecraft:stick". Always compared. */
    public final String id;
    /** Plain-text custom name or null. */
    public final String customName;
    /** Canonical, sorted enchantment description ("" if none). */
    public final String enchantments;
    /** Damage (durability used). */
    public final int damage;
    /** Hash of all non-default components ("" if none). */
    public final String components;

    public Fingerprint(String id, String customName, String enchantments, int damage, String components) {
        this.id = id;
        this.customName = customName;
        this.enchantments = enchantments == null ? "" : enchantments;
        this.damage = damage;
        this.components = components == null ? "" : components;
    }

    public static Fingerprint capture(ItemStack stack) {
        return new Fingerprint(
                Registries.ITEM.getId(stack.getItem()).toString(),
                customName(stack),
                enchantments(stack),
                stack.getDamage(),
                componentsHash(stack)
        );
    }

    // ---- facet extraction (shared with ItemMatcher) ----

    public static String customName(ItemStack stack) {
        Text name = stack.get(DataComponentTypes.CUSTOM_NAME);
        return name == null ? null : name.getString();
    }

    public static String enchantments(ItemStack stack) {
        List<String> parts = new ArrayList<>();
        addEnchantments(parts, "e", stack.get(DataComponentTypes.ENCHANTMENTS));
        addEnchantments(parts, "s", stack.get(DataComponentTypes.STORED_ENCHANTMENTS));
        Collections.sort(parts);
        return String.join(",", parts);
    }

    private static void addEnchantments(List<String> out, String prefix, ItemEnchantmentsComponent component) {
        if (component == null) {
            return;
        }
        for (RegistryEntry<Enchantment> entry : component.getEnchantments()) {
            String id = entry.getKey().map(key -> key.getValue().toString()).orElse("unknown");
            out.add(prefix + ":" + id + "@" + component.getLevel(entry));
        }
    }

    /**
     * Hash over every component that differs from the item's defaults, encoded through the
     * components' own codecs so the result is stable between game sessions.
     */
    @SuppressWarnings("unchecked")
    public static String componentsHash(ItemStack stack) {
        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null) {
            return "";
        }
        RegistryOps<JsonElement> ops = world.getRegistryManager().getOps(JsonOps.INSTANCE);
        List<String> parts = new ArrayList<>();
        for (Map.Entry<ComponentType<?>, Optional<?>> entry : stack.getComponentChanges().entrySet()) {
            Identifier typeId = Registries.DATA_COMPONENT_TYPE.getId(entry.getKey());
            if (typeId == null) {
                continue;
            }
            if (entry.getValue().isEmpty()) {
                parts.add("!" + typeId);
                continue;
            }
            Codec<Object> codec = (Codec<Object>) (Codec<?>) entry.getKey().getCodec();
            if (codec == null) {
                continue; // transient component, never persisted
            }
            String encoded = codec.encodeStart(ops, entry.getValue().get())
                    .result()
                    .map(JsonElement::toString)
                    .orElse("?");
            parts.add(typeId + "=" + encoded);
        }
        if (parts.isEmpty()) {
            return "";
        }
        Collections.sort(parts);
        return UUID.nameUUIDFromBytes(String.join("\n", parts).getBytes(StandardCharsets.UTF_8)).toString();
    }

    // ---- JSON ----

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        if (customName != null) {
            o.addProperty("customName", customName);
        }
        o.addProperty("enchantments", enchantments);
        o.addProperty("damage", damage);
        o.addProperty("components", components);
        return o;
    }

    /** Returns null if the entry is unusable. */
    public static Fingerprint fromJson(JsonObject o) {
        if (!o.has("id") || !o.get("id").isJsonPrimitive()) {
            return null;
        }
        String id = o.get("id").getAsString();
        if (Identifier.tryParse(id) == null) {
            return null;
        }
        return new Fingerprint(
                id,
                o.has("customName") && o.get("customName").isJsonPrimitive() ? o.get("customName").getAsString() : null,
                o.has("enchantments") && o.get("enchantments").isJsonPrimitive() ? o.get("enchantments").getAsString() : "",
                o.has("damage") && o.get("damage").isJsonPrimitive() ? o.get("damage").getAsInt() : 0,
                o.has("components") && o.get("components").isJsonPrimitive() ? o.get("components").getAsString() : ""
        );
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Fingerprint f)) {
            return false;
        }
        return id.equals(f.id) && Objects.equals(customName, f.customName)
                && enchantments.equals(f.enchantments) && damage == f.damage && components.equals(f.components);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, customName, enchantments, damage, components);
    }
}
