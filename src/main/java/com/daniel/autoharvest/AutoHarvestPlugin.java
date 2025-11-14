package com.daniel.autoharvest;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public class AutoHarvestPlugin extends JavaPlugin implements Listener {

    private static final Map<Material, Material> SEED_MAP = new EnumMap<>(Material.class);

    static {
        SEED_MAP.put(Material.WHEAT, Material.WHEAT_SEEDS);
        SEED_MAP.put(Material.CARROTS, Material.CARROT);
        SEED_MAP.put(Material.POTATOES, Material.POTATO);
        SEED_MAP.put(Material.BEETROOTS, Material.BEETROOT_SEEDS);
        SEED_MAP.put(Material.NETHER_WART, Material.NETHER_WART);
        SEED_MAP.put(Material.COCOA, Material.COCOA_BEANS);
    }

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("[AutoHarvest] Activado (3x3, mano vacía o azada).");
    }

    @Override
    public void onDisable() {
        getLogger().info("[AutoHarvest] Desactivado.");
    }

    @EventHandler
    public void onPlayerInteractCrop(PlayerInteractEvent event) {
        // Solo RIGHT_CLICK_BLOCK y mano principal
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Block clicked = event.getClickedBlock();
        if (clicked == null) return;

        Material clickedMat = clicked.getType();
        // Si el bloque no es cultivo conocido, salimos
        if (!SEED_MAP.containsKey(clickedMat)) return;

        Player player = event.getPlayer();
        ItemStack inHand = player.getInventory().getItemInMainHand();
        Material inHandMat = (inHand == null) ? Material.AIR : inHand.getType();

        // Solo permitir si mano vacía o una azada
        if (!(inHandMat == Material.AIR || isHoe(inHandMat))) return;

        // Cancelamos la interacción por defecto (evita efectos no deseados)
        event.setCancelled(true);

        int harvested = 0;
        int replanted = 0;

        // Recorremos 3x3 centrado en el bloque clicado
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {

                Block b = clicked.getRelative(dx, 0, dz);
                Material type = b.getType();

                if (!SEED_MAP.containsKey(type)) continue;
                if (!(b.getBlockData() instanceof Ageable)) continue;

                Ageable ageable = (Ageable) b.getBlockData();
                if (ageable.getAge() < ageable.getMaximumAge()) continue; // no maduro

                // Obtener drops usando la herramienta en mano del jugador
                Collection<ItemStack> drops = b.getDrops(player.getInventory().getItemInMainHand());

                // Intentar añadir las drops al inventario; si sobra, soltarlas en el suelo
                for (ItemStack drop : drops) {
                    HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(drop);
                    if (!leftover.isEmpty()) {
                        for (ItemStack left : leftover.values()) {
                            Location dropLoc = b.getLocation().add(0.5, 0.5, 0.5);
                            b.getWorld().dropItemNaturally(dropLoc, left);
                        }
                    }
                }

                harvested++;

                // Intento replantar si el jugador tiene la semilla correspondiente
                Material seed = SEED_MAP.get(type);
                if (seed != null && hasOneSeed(player, seed)) {
                    boolean removed = removeOneSeed(player, seed);
                    if (removed) {
                        b.setType(type, false);
                        if (b.getBlockData() instanceof Ageable) {
                            Ageable newAge = (Ageable) b.getBlockData();
                            newAge.setAge(0);
                            b.setBlockData(newAge, false);
                        }
                        replanted++;
                    } else {
                        b.setType(Material.AIR, false);
                    }
                } else {
                    // No hay semilla para replantar
                    b.setType(Material.AIR, false);
                }
            }
        }

        // Feedback
        if (harvested > 0) {
            player.playSound(player.getLocation(), Sound.BLOCK_CROP_BREAK, 1f, 1f);
            player.sendMessage("§aCosechados " + harvested + " cultivos. Replantados: " + replanted + ".");
        }
    }

    // Comprueba si el material es una azada
    private boolean isHoe(Material mat) {
        return mat == Material.WOODEN_HOE
                || mat == Material.STONE_HOE
                || mat == Material.IRON_HOE
                || mat == Material.GOLDEN_HOE
                || mat == Material.DIAMOND_HOE
                || mat == Material.NETHERITE_HOE;
    }

    // Comprueba si el jugador tiene al menos una unidad del material indicado
    private boolean hasOneSeed(Player p, Material seed) {
        return p.getInventory().contains(seed, 1);
    }

    // Quita exactamente 1 unidad del material indicado del inventario del jugador y devuelve true si lo pudo quitar
    private boolean removeOneSeed(Player p, Material seed) {
        for (int i = 0; i < p.getInventory().getSize(); i++) {
            ItemStack it = p.getInventory().getItem(i);
            if (it == null) continue;
            if (it.getType() == seed) {
                if (it.getAmount() > 1) {
                    it.setAmount(it.getAmount() - 1);
                    p.getInventory().setItem(i, it);
                } else {
                    p.getInventory().setItem(i, null);
                }
                return true;
            }
        }
        return false;
    }
}
