/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package dev.espi.protectionstones.commands;

import dev.espi.protectionstones.*;
import dev.espi.protectionstones.utils.ChatUtil;
import dev.espi.protectionstones.utils.SchedulerUtil;
import dev.espi.protectionstones.utils.UUIDCache;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ArgTp implements PSCommandArg {

    private static final ConcurrentMap<UUID, ScheduledTask> taskCounter = new ConcurrentHashMap<>();

    // /ps tp, /ps home

    @Override
    public List<String> getNames() {
        return Collections.singletonList("tp");
    }

    @Override
    public boolean allowNonPlayersToExecute() {
        return false;
    }

    @Override
    public List<String> getPermissionsToExecute() {
        return Collections.singletonList("protectionstones.tp");
    }

    @Override
    public HashMap<String, Boolean> getRegisteredFlags() {
        return null;
    }

    @Override
    public boolean executeArgument(CommandSender s, String[] args, HashMap<String, String> flags) {
        Player p = (Player) s;

        // preliminary checks
        if (!p.hasPermission("protectionstones.tp"))
            return PSL.msg(p, PSL.NO_PERMISSION_TP.msg());

        if (args.length < 2 || args.length > 3)
            return PSL.msg(p, PSL.TP_HELP.msg());

        if (args.length == 2) { // /ps tp [name/id]
            SchedulerUtil.executeForEntity(p, () -> {
                // get regions from the query
                List<PSRegion> regions = ProtectionStones.getPSRegions(p.getWorld(), args[1]);

                if (regions.isEmpty()) {
                    PSL.msg(s, PSL.REGION_DOES_NOT_EXIST.msg());
                    return;
                }
                if (regions.size() > 1) {
                    ChatUtil.displayDuplicateRegionAliases(p, regions);
                    return;
                }
                teleportPlayer(p, regions.get(0));
            });
        } else { // /ps tp [player] [num]
            // get the region id the player wants to teleport to
            int regionNumber;
            try {
                regionNumber = Integer.parseInt(args[2]);
                if (regionNumber <= 0) {
                    return PSL.msg(p, PSL.NUMBER_ABOVE_ZERO.msg());
                }
            } catch (NumberFormatException e) {
                return PSL.msg(p, PSL.TP_VALID_NUMBER.msg());
            }

            String tpName = args[1];
            // region checks, and set lp to offline player
            if (!UUIDCache.containsName(tpName)) {
                return PSL.msg(p, PSL.PLAYER_NOT_FOUND.msg());
            }
            UUID tpUuid = UUIDCache.getUUIDFromName(tpName);

            // run region search asynchronously to avoid blocking server thread
            SchedulerUtil.executeForEntity(p, () -> {
                List<PSRegion> regions = PSPlayer.fromUUID(tpUuid).getPSRegionsCrossWorld(p.getWorld(), false);

                // check if region was found
                if (regions.isEmpty()) {
                    PSL.msg(p, PSL.REGION_NOT_FOUND_FOR_PLAYER.msg()
                            .replace("%player%", tpName));
                    return;
                } else if (regionNumber > regions.size()) {
                    PSL.msg(p, PSL.ONLY_HAS_REGIONS.msg()
                            .replace("%player%", tpName)
                            .replace("%num%", "" + regions.size()));
                    return;
                }

                teleportPlayer(p, regions.get(regionNumber - 1));
            });
        }

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        return null;
    }

    static void teleportPlayer(Player p, PSRegion r) {
        if (r.getTypeOptions() == null) {
            PSL.msg(p, ChatColor.RED + "This region is problematic, and the block type (" + r.getType() + ") is not configured. Please contact an administrator.");
            Bukkit.getLogger().info(ChatColor.RED + "This region is problematic, and the block type (" + r.getType() + ") is not configured.");
            return;
        }

        // teleport player
        if (r.getTypeOptions().tpWaitingSeconds == 0 || p.hasPermission("protectionstones.tp.bypasswait")) {
            // no teleport delay
            PSL.msg(p, PSL.TPING.msg());
            Location destination = r.getHome();
            if (destination != null) p.teleportAsync(destination);
        } else if (!r.getTypeOptions().noMovingWhenTeleportWaiting) {
            // teleport delay, but doesn't care about moving
            p.sendMessage(PSL.TP_IN_SECONDS.msg().replace("%seconds%", "" + r.getTypeOptions().tpWaitingSeconds));

            Location destination = r.getHome();
            SchedulerUtil.laterForEntity(p, () -> {
                PSL.msg(p, PSL.TPING.msg());
                if (destination != null) p.teleportAsync(destination);
            }, 20L * r.getTypeOptions().tpWaitingSeconds);

        } else {// delay and not allowed to move
            PSL.msg(p, PSL.TP_IN_SECONDS.msg().replace("%seconds%", "" + r.getTypeOptions().tpWaitingSeconds));
            Location l = p.getLocation().clone();
            UUID uuid = p.getUniqueId();

            // remove queued teleport if already running
            ScheduledTask previousTask = taskCounter.remove(uuid);
            if (previousTask != null) previousTask.cancel();

            // EntityScheduler follows the player through region changes.
            AtomicInteger elapsedChecks = new AtomicInteger();
            Location destination = r.getHome();
            ScheduledTask scheduledTask = SchedulerUtil.repeatForEntity(p, task -> {
                Location current = p.getLocation();
                ProtectionStones.getInstance().debug(String.format("Checking player movement. Player location: (%.2f, %.2f, %.2f), actual location: (%.2f, %.2f, %.2f)", current.getX(), current.getY(), current.getZ(), l.getX(), l.getY(), l.getZ()));

                if (!inThreshold(l.getX(), current.getX()) || !inThreshold(l.getY(), current.getY()) || !inThreshold(l.getZ(), current.getZ())) {
                    ProtectionStones.getInstance().debug(String.format("Not in threshold. X check: %s, Y check: %s, Z check: %s", inThreshold(l.getX(), current.getX()), inThreshold(l.getY(), current.getY()), inThreshold(l.getZ(), current.getZ())));
                    PSL.msg(p, PSL.TP_CANCELLED_MOVED.msg());
                    removeUUIDTimer(uuid);
                } else if (elapsedChecks.incrementAndGet() >= r.getTypeOptions().tpWaitingSeconds * 4) { // four checks per second
                    PSL.msg(p, PSL.TPING.msg());
                    if (destination != null) p.teleportAsync(destination);
                    removeUUIDTimer(uuid);
                }
            }, 5L, 5L);
            if (scheduledTask != null) taskCounter.put(uuid, scheduledTask);
        }
    }

    private static boolean inThreshold(double location, double playerLoc) {
        return playerLoc <= location + 1.0 && playerLoc >= location - 1.0;
    }

    private static void removeUUIDTimer(UUID uuid) {
        ScheduledTask task = taskCounter.remove(uuid);
        if (task != null) task.cancel();
    }
}
