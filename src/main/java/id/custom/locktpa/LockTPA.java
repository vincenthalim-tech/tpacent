package id.custom.locktpa;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class LockTPA extends JavaPlugin implements CommandExecutor {

    private TpaManager tpaManager;

    @Override
    public void onEnable() {
        // Otomatis membuat folder plugin & file config.yml jika belum ada
        saveDefaultConfig();
        this.tpaManager = new TpaManager(this);

        String[] commands = {"tpa", "tpahere", "tpaccept", "tpdeny", "tpaauto", "tpareload"};
        for (String cmd : commands) {
            if (getCommand(cmd) != null) {
                getCommand(cmd).setExecutor(this);
            }
        }

        getLogger().info("LockTPA berhasil diaktifkan!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase();

        if (cmd.equals("tpareload")) {
            reloadConfig();
            sender.sendMessage(tpaManager.format("reloaded", "", 0));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(tpaManager.format("only-player", "", 0));
            return true;
        }

        switch (cmd) {
            case "tpa":
            case "tpahere":
                if (args.length < 1) {
                    player.sendMessage("§cGunakan: /" + cmd + " <nama_player>");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[0]);
                if (target == null || !target.isOnline()) {
                    player.sendMessage(tpaManager.format("player-not-found", "", 0));
                    return true;
                }
                if (target.getUniqueId().equals(player.getUniqueId())) {
                    player.sendMessage(tpaManager.format("self-request", "", 0));
                    return true;
                }
                tpaManager.sendRequest(player, target, cmd.equals("tpahere"));
                return true;

            case "tpaccept":
                tpaManager.acceptRequest(player);
                return true;

            case "tpdeny":
                tpaManager.denyRequest(player);
                return true;

            case "tpaauto":
                boolean enabled = tpaManager.toggleAutoAccept(player.getUniqueId());
                if (enabled) {
                    player.sendMessage(tpaManager.format("auto-enabled", "", 0));
                } else {
                    player.sendMessage(tpaManager.format("auto-disabled", "", 0));
                }
                return true;
        }

        return true;
    }
}
