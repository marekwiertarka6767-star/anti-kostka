package dev.antikostka.match;

import dev.antikostka.config.AntiKostkaConfig;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

import java.util.Objects;

/**
 * Decides whether a live stack satisfies a saved fingerprint.
 * <p>
 * The stack count is never read here, so 1, 12, 32 or 64 of the same item all match.
 */
public final class ItemMatcher {
    private ItemMatcher() {
    }

    public static boolean matches(Fingerprint saved, ItemStack stack) {
        if (saved == null || stack.isEmpty()) {
            return false;
        }
        // BASIC: registry id only (works for modded items too).
        if (!saved.id.equals(Registries.ITEM.getId(stack.getItem()).toString())) {
            return false;
        }

        AntiKostkaConfig cfg = AntiKostkaConfig.get();
        if (cfg.matchMode != AntiKostkaConfig.MatchMode.ADVANCED) {
            return true;
        }

        // ADVANCED: only the facets the user enabled are compared.
        if (cfg.matchEnchantments && !saved.enchantments.equals(Fingerprint.enchantments(stack))) {
            return false;
        }
        if (cfg.matchCustomName && !Objects.equals(saved.customName, Fingerprint.customName(stack))) {
            return false;
        }
        if (cfg.matchDurability && saved.damage != stack.getDamage()) {
            return false;
        }
        if (cfg.matchAllComponents && !saved.components.equals(Fingerprint.componentsHash(stack))) {
            return false;
        }
        return true;
    }
}
