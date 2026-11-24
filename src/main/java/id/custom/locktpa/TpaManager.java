package id.custom.locktpa;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class TpaManager {

    private final LockTPA plugin;
    private final Map<UUID, TpaRequest> pendingRequests = new HashMap<>();
    private final Set<UUID> autoAcceptPlayers = new HashSet<>();

    public TpaManager(LockTPA plugin) {
        this.plugin = plugin;
    }

    public static class TpaRequest {
        public final UUID senderId;
        public final UUID targetId;
        public final boolean isTpaHere;
        public final long timestamp;

        public TpaRequest(UUID senderId, UUID targetId, boolean isTpaHere) {
            this.senderId = senderId;
            this.targetId = targetId;
            this.isTpaHere = isTpaHere;
            this.timestamp = System.currentTimeMillis();
        }
    }

    public void sendRequest(Player sender, Player target, boolean isTpaHere) {
        TpaRequest request = new TpaRequest(sender.getUniqueId(), target.getUniqueId(), isTpaHere);
        pendingRequests.put(target.getUniqueId(), request);

        sender.sendMessage(format("request-sent", target.getName(), 0));

        if (isTpaHere) {
            target.sendMessage(format("request-here-received", sender.getName(), 0));
        } else {
            target.sendMessage(format("request-received", sender.getName(), 0));
        }

        // Cek jika target mengaktifkan /tpaauto
        if (isAutoAccept(target.getUniqueId())) {
            acceptRequest(target);
        }
    }

    public void acceptRequest(Player target) {
        TpaRequest request = pendingRequests.remove(target.getUniqueId());
        if (request == null) {
            target.sendMessage(format("no-pending", "", 0));
            return;
        }

        int expireSeconds = plugin.getConfig().getInt("settings.request-expire", 60);
        if ((System.currentTimeMillis() - request.timestamp) > (expireSeconds * 1000L)) {
            target.sendMessage(format("request-expired", "", 0));
            return;
        }

        Player sender = Bukkit.getPlayer(request.senderId);
        if (sender == null || !sender.isOnline()) {
            target.sendMessage(format("player-not-found", "", 0));
            return;
        }

        // Tentukan siapa yang diteleportasi dan di mana koordinat yang DIKUNCI
        final Player playerToTeleport = request.isTpaHere ? target : sender;
        final Player anchorPlayer = request.isTpaHere ? sender : target;

        // LOCK LOKASI: .clone() mengunci koordinat X, Y, Z, Yaw, Pitch pada detik ini.
        // Jika anchorPlayer berlari menjauh setelah ini, lockedLocation TIDAK akan berubah.
        final Location lockedLocation = anchorPlayer.getLocation().clone();
        final Location initialSenderLoc = playerToTeleport.getLocation().clone();

        int delay = plugin.getConfig().getInt("settings.teleport-delay", 3);

        if (delay <= 0) {
            executeTeleport(playerToTeleport, lockedLocation);
            return;
        }

        anchorPlayer.sendMessage(format("location-locked", playerToTeleport.getName(), delay));
        playerToTeleport.sendMessage(format("teleporting-in", anchorPlayer.getName(), delay));

        new BukkitRunnable() {
            int countdown = delay;

            @Override
            public void run() {
                if (!playerToTeleport.isOnline()) {
                    cancel();
                    return;
                }

                // Opsional: batalkan jika pemain yang mau TP bergerak
                if (plugin.getConfig().getBoolean("settings.cancel-on-move", false)) {
                    if (playerToTeleport.getLocation().distanceSquared(initialSenderLoc) > 0.25) {
                        playerToTeleport.sendMessage(ChatColor.RED + "Teleportasi dibatalkan karena kamu bergerak!");
                        cancel();
                        return;
                    }
                }

                if (countdown <= 0) {
                    executeTeleport(playerToTeleport, lockedLocation);
                    cancel();
                    return;
                }

                countdown--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void executeTeleport(Player player, Location lockedLocation) {
        player.teleport(lockedLocation);

        // Pastikan tidak ada kebal/protection setelah teleport sehingga langsung bisa dipukul
        if (plugin.getConfig().getBoolean("settings.instant-pvp-vulnerable", true)) {
            player.setNoDamageTicks(0);
        }

        player.sendMessage(format("teleported", "", 0));
    }

    public void denyRequest(Player target) {
        TpaRequest request = pendingRequests.remove(target.getUniqueId());
        if (request == null) {
            target.sendMessage(format("no-pending", "", 0));
            return;
        }

        Player sender = Bukkit.getPlayer(request.senderId);
        target.sendMessage(format("request-denied-target", sender != null ? sender.getName() : "Unknown", 0));

        if (sender != null && sender.isOnline()) {
            sender.sendMessage(format("request-denied-sender", target.getName(), 0));
        }
    }

    public boolean toggleAutoAccept(UUID playerId) {
        if (autoAcceptPlayers.contains(playerId)) {
            autoAcceptPlayers.remove(playerId);
            return false;
        } else {
            autoAcceptPlayers.add(playerId);
            return true;
        }
    }

    public boolean isAutoAccept(UUID playerId) {
        return autoAcceptPlayers.contains(playerId);
    }

    public String format(String path, String playerName, int time) {
        String prefix = plugin.getConfig().getString("messages.prefix", "");
        String msg = plugin.getConfig().getString("messages." + path, "");
        msg = msg.replace("%player%", playerName).replace("%time%", String.valueOf(time));
        return ChatColor.translateAlternateColorCodes('&', prefix + msg);
    }
}
