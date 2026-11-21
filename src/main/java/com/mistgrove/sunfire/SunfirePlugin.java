package com.mistgrove.sunfire;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class SunfirePlugin extends JavaPlugin implements Listener, TabCompleter {

    private NamespacedKey abilityKey;
    private final MiniMessage mm = MiniMessage.miniMessage();

    private final Map<UUID, UUID> guiTargets = new HashMap<>();

    private final Map<UUID, Long> dashCooldowns = new HashMap<>();
    private final Map<UUID, Long> groundCooldowns = new HashMap<>();
    private final Map<UUID, Long> doubleJumpCooldowns = new HashMap<>();

    private final Set<UUID> activeSlammers = new HashSet<>();
    private final Set<UUID> frozenPlayers = new HashSet<>();
    private final Set<UUID> fallImmune = new HashSet<>();

    private static final long COOLDOWN_MS = 5000;
    private final Component guiTitle = mm.deserialize("<gradient:#ff512f:#f09819><bold>sᴜɴꜰɪʀᴇ ᴀʙɪʟɪᴛɪᴇs</bold></gradient>");

    @Override
    public void onEnable() {
        abilityKey = new NamespacedKey(this, "sunfire_ability");
        getServer().getPluginManager().registerEvents(this, this);
        if (getCommand("sunfire") != null) {
            getCommand("sunfire").setTabCompleter(this);
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player admin)) {
            sender.sendMessage("Only players can open the Sunfire GUI.");
            return true;
        }

        if (args.length < 1) {
            admin.sendMessage(mm.deserialize("<red>Usage: /sunfire <player></red>"));
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            admin.sendMessage(mm.deserialize("<red>Player not found!</red>"));
            return true;
        }

        if (target.getInventory().getItemInMainHand().getType() == Material.AIR) {
            admin.sendMessage(mm.deserialize("<red>" + target.getName() + " must be holding an item in their main hand!</red>"));
            return true;
        }

        openSunfireGUI(admin, target);
        return true;
    }

    private void openSunfireGUI(Player admin, Player target) {
        Inventory gui = Bukkit.createInventory(null, 27, guiTitle);
        guiTargets.put(admin.getUniqueId(), target.getUniqueId());

        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        if (fillerMeta != null) {
            fillerMeta.displayName(Component.empty());
            filler.setItemMeta(fillerMeta);
        }
        for (int i = 0; i < 27; i++) {
            gui.setItem(i, filler);
        }

        gui.setItem(11, createGuiIcon(
                Material.FEATHER,
                "<gradient:#ffffff:#a8edea><bold>ᴅᴀsʜ</bold></gradient>",
                List.of(
                        "<gray>ᴋᴇʏʙɪɴᴅ: <white>ᴠ (ᴏꜰꜰʜᴀɴᴅ sᴡᴀᴘ)</white></gray>",
                        "<gray>ᴄᴏᴏʟᴅᴏᴡɴ: <white>5s</white></gray>",
                        "",
                        "<yellow>ᴄʟɪᴄᴋ ᴛᴏ ᴀᴘᴘʟʏ ᴛᴏ " + target.getName() + "'s ɪᴛᴇᴍ</yellow>"
                )
        ));

        gui.setItem(13, createGuiIcon(
                Material.MACE,
                "<gradient:#ff512f:#f09819><bold>ɢʀᴏᴜɴᴅ sʟᴀᴍ</bold></gradient>",
                List.of(
                        "<gray>ᴋᴇʏʙɪɴᴅ: <white>ɢ (ᴅʀᴏᴘ ɪɴ ᴍɪᴅᴀɪʀ)</white></gray>",
                        "<gray>ʀᴀᴅɪᴜs: <white>5 ʙʟᴏᴄᴋs (ꜰʀᴇᴇᴢᴇs ᴇɴᴇᴍɪᴇs)</white></gray>",
                        "<gray>ᴄᴏᴏʟᴅᴏᴡɴ: <white>5s</white></gray>",
                        "",
                        "<yellow>ᴄʟɪᴄᴋ ᴛᴏ ᴀᴘᴘʟʏ ᴛᴏ " + target.getName() + "'s ɪᴛᴇᴍ</yellow>"
                )
        ));

        gui.setItem(15, createGuiIcon(
                Material.WIND_CHARGE,
                "<gradient:#00f2fe:#4facfe><bold>ᴅᴏᴜʙʟᴇ ᴊᴜᴍᴘ</bold></gradient>",
                List.of(
                        "<gray>ᴋᴇʏʙɪɴᴅ: <white>sᴘᴀᴄᴇʙᴀʀ (2x ᴊᴜᴍᴘ)</white></gray>",
                        "<gray>ᴄᴏᴏʟᴅᴏᴡɴ: <white>5s</white></gray>",
                        "",
                        "<yellow>ᴄʟɪᴄᴋ ᴛᴏ ᴀᴘᴘʟʏ ᴛᴏ " + target.getName() + "'s ɪᴛᴇᴍ</yellow>"
                )
        ));

        admin.openInventory(gui);
    }

    private ItemStack createGuiIcon(Material mat, String name, List<String> loreLines) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(mm.deserialize(name).decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            for (String line : loreLines) {
                lore.add(mm.deserialize(line).decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onGuiClick(InventoryClickEvent event) {
        if (!event.getView().title().equals(guiTitle)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player admin)) return;
        UUID targetId = guiTargets.get(admin.getUniqueId());
        if (targetId == null) return;

        Player target = Bukkit.getPlayer(targetId);
        if (target == null) {
            admin.closeInventory();
            admin.sendMessage(mm.deserialize("<red>Target player went offline!</red>"));
            return;
        }

        ItemStack heldItem = target.getInventory().getItemInMainHand();
        if (heldItem.getType() == Material.AIR) {
            admin.closeInventory();
            admin.sendMessage(mm.deserialize("<red>" + target.getName() + " is no longer holding an item!</red>"));
            return;
        }

        int slot = event.getRawSlot();
        String chosenAbility = switch (slot) {
            case 11 -> "DASH";
            case 13 -> "GROUND";
            case 15 -> "DOUBLE_JUMP";
            default -> null;
        };

        if (chosenAbility == null) return;

        ItemMeta meta = heldItem.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(abilityKey, PersistentDataType.STRING, chosenAbility);
            List<Component> lore = meta.hasLore() && meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();

            switch (chosenAbility) {
                case "DASH" -> {
                    lore.add(mm.deserialize("<gradient:#ffffff:#a8edea>✦ ᴀʙɪʟɪᴛʏ: ᴅᴀsʜ [ᴠ]</gradient>").decoration(TextDecoration.ITALIC, false));
                    admin.sendMessage(mm.deserialize("<green>Applied <white>Dash</white> to " + target.getName() + "'s item!</green>"));
                }
                case "GROUND" -> {
                    lore.add(mm.deserialize("<gradient:#ff512f:#f09819>✦ ᴀʙɪʟɪᴛʏ: ɢʀᴏᴜɴᴅ sʟᴀᴍ [ɢ]</gradient>").decoration(TextDecoration.ITALIC, false));
                    admin.sendMessage(mm.deserialize("<green>Applied <gold>Ground Slam</gold> to " + target.getName() + "'s item!</green>"));
                }
                case "DOUBLE_JUMP" -> {
                    lore.add(mm.deserialize("<gradient:#00f2fe:#4facfe>✦ ᴀʙɪʟɪᴛʏ: ᴅᴏᴜʙʟᴇ ᴊᴜᴍᴘ [sᴘᴀᴄᴇ]</gradient>").decoration(TextDecoration.ITALIC, false));
                    admin.sendMessage(mm.deserialize("<green>Applied <aqua>Double Jump</aqua> to " + target.getName() + "'s item!</green>"));
                }
            }

            meta.lore(lore);
            heldItem.setItemMeta(meta);
        }

        admin.playSound(admin.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.2f);
        admin.closeInventory();
        guiTargets.remove(admin.getUniqueId());
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return null;
        }
        return List.of();
    }

    private String getAbility(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(abilityKey, PersistentDataType.STRING);
    }

    private boolean isOnCooldown(Player player, Map<UUID, Long> map, String abilityName) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        if (map.containsKey(uuid)) {
            long timeLeft = (map.get(uuid) + COOLDOWN_MS) - now;
            if (timeLeft > 0) {
Use code with caution.
double sec = Math.ceil(timeLeft / 100.0) / 10.0;
player.sendActionBar(mm.deserialize("" + abilityName + " ᴄᴏᴏʟᴅᴏᴡɴ: " + sec + "s"));
return true;
}
}
map.put(uuid, now);
return false;
}
@EventHandler
public void onSwapHand(PlayerSwapHandItemsEvent event) {
Player player = event.getPlayer();
String ability = getAbility(event.getOffHandItem());
if ("DASH".equals(ability)) {
event.setCancelled(true);
if (isOnCooldown(player, dashCooldowns, "ᴅᴀsʜ")) return;
Vector dir = player.getLocation().getDirection().normalize().multiply(1.7);
dir.setY(Math.max(dir.getY(), 0.35));
player.setVelocity(dir);
player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation().add(0, 1, 0), 15, 0.25, 0.25, 0.25, 0.05);
player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BREEZE_SLIDE, 1.0f, 1.3f);
player.sendActionBar(mm.deserialize("✦ ᴅᴀsʜᴇᴅ!"));
}
}
@EventHandler
public void onDropItem(PlayerDropItemEvent event) {
Player player = event.getPlayer();
ItemStack dropped = event.getItemDrop().getItemStack();
String ability = getAbility(dropped);
if ("GROUND".equals(ability) && !player.isOnGround()) {
event.setCancelled(true);
if (isOnCooldown(player, groundCooldowns, "ɢʀᴏᴜɴᴅ sʟᴀᴍ")) return;
activeSlammers.add(player.getUniqueId());
fallImmune.add(player.getUniqueId());
player.setVelocity(new Vector(0, -2.5, 0));
player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITHER_SHOOT, 0.8f, 0.7f);
player.sendActionBar(mm.deserialize("✦ ɢʀᴏᴜɴᴅ sʟᴀᴍ!"));
}
}
@EventHandler
public void onPlayerMove(PlayerMoveEvent event) {
Player player = event.getPlayer();
UUID uuid = player.getUniqueId();
if (frozenPlayers.contains(uuid)) {
if (event.getFrom().getX() != event.getTo().getX() || event.getFrom().getZ() != event.getTo().getZ()) {
Location lock = event.getFrom().clone();
lock.setYaw(event.getTo().getYaw());
lock.setPitch(event.getTo().getPitch());
event.setTo(lock);
}
return;
}
if (activeSlammers.contains(uuid) && player.isOnGround()) {
activeSlammers.remove(uuid);
triggerGroundSlamImpact(player);
}
if (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE) {
String ability = getAbility(player.getInventory().getItemInMainHand());
if ("DOUBLE_JUMP".equals(ability)) {
long lastUsed = doubleJumpCooldowns.getOrDefault(uuid, 0L);
if (System.currentTimeMillis() - lastUsed >= COOLDOWN_MS) {
if (player.isOnGround()) {
player.setAllowFlight(true);
}
}
} else if (player.getAllowFlight()) {
player.setAllowFlight(false);
player.setFlying(false);
}
}
}
@EventHandler
public void onToggleFlight(PlayerToggleFlightEvent event) {
Player player = event.getPlayer();
if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
String ability = getAbility(player.getInventory().getItemInMainHand());
if ("DOUBLE_JUMP".equals(ability)) {
event.setCancelled(true);
player.setAllowFlight(false);
player.setFlying(false);
if (isOnCooldown(player, doubleJumpCooldowns, "ᴅᴏᴜʙʟᴇ ᴊᴜᴍᴘ")) return;
fallImmune.add(player.getUniqueId());
Vector jump = player.getLocation().getDirection().multiply(0.6).setY(0.85);
player.setVelocity(jump);
player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 25, 0.4, 0.1, 0.4, 0.05);
player.getWorld().spawnParticle(Particle.FLAME, player.getLocation(), 10, 0.3, 0.1, 0.3, 0.02);
player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1.0f, 1.2f);
player.sendActionBar(mm.deserialize("✦ ᴅᴏᴜʙʟᴇ ᴊᴜᴍᴘ!"));
}
}
private void triggerGroundSlamImpact(Player slammer) {
Location loc = slammer.getLocation();
slammer.getWorld().spawnParticle(Particle.EXPLOSION, loc, 3, 0.5, 0.2, 0.5, 0);
slammer.getWorld().spawnParticle(Particle.FLAME, loc, 40, 2.5, 0.2, 2.5, 0.05);
slammer.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, loc, 30, 2.5, 0.2, 2.5, 0.02);
slammer.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.9f);
for (Entity entity : slammer.getNearbyEntities(5.0, 5.0, 5.0)) {
if (entity instanceof Player target && !target.getUniqueId().equals(slammer.getUniqueId())) {
UUID targetId = target.getUniqueId();
frozenPlayers.add(targetId);
target.setFreezeTicks(100);
target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 10, false, false));
target.getWorld().spawnParticle(Particle.SNOWFLAKE, target.getLocation().add(0, 1, 0), 25, 0.4, 0.5, 0.4, 0.02);
target.sendActionBar(mm.deserialize("❄ ꜰʀᴏᴢᴇɴ ʙʏ " + slammer.getName() + "'s ɢʀᴏᴜɴᴅ sʟᴀᴍ!"));
new BukkitRunnable() {
@Override
public void run() {
frozenPlayers.remove(targetId);
}
}.runTaskLater(this, 60L);
}
}
}
@EventHandler
public void onFallDamage(EntityDamageEvent event) {
if (event.getEntity() instanceof Player player && event.getCause() == EntityDamageEvent.DamageCause.FALL) {
if (fallImmune.remove(player.getUniqueId())) {
event.setCancelled(true);
}
}
}
}
