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

import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import dev.espi.protectionstones.*;
import dev.espi.protectionstones.utils.WGUtils;
import dev.espi.protectionstones.utils.SchedulerUtil;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.CommandSender;

import java.util.function.Consumer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

class ArgAdminChangeblock {

    // /ps admin changeblock [world] [fromblockalias] [toblockalias]
    static boolean argumentAdminChangeblock(CommandSender p, String[] args) {
        if (args.length < 5) {
            PSL.msg(p, ArgAdmin.getChangeBlockHelp());
            return true;
        }

        String world = args[2], fromBlockAlias = args[3], toBlockAlias = args[4];
        if (ProtectionStones.getProtectBlockFromAlias(fromBlockAlias) == null) {
            PSL.msg(p, ChatColor.GRAY + "The type to change from is not a registered protection block!");
            return true;
        }
        if (ProtectionStones.getProtectBlockFromAlias(toBlockAlias) == null) {
            PSL.msg(p, ChatColor.GRAY + "The type to change to is not a registered protection block!");
            return true;
        }

        String fromBlock = ProtectionStones.getProtectBlockFromAlias(fromBlockAlias).type,
                toBlock = ProtectionStones.getProtectBlockFromAlias(toBlockAlias).type;

        SchedulerUtil.executeGlobally(() -> {
        World w = WGUtils.getLoadedWorld(world);
        if (w == null) {
            PSL.msg(p, PSL.INVALID_WORLD.msg());
            return;
        }
        RegionManager rgm = WGUtils.getRegionManagerWithWorld(w);
        if (rgm == null) {
            PSL.msg(p, ChatColor.GRAY + "The world does not have WorldGuard configured!");
            return;
        }
        List<PSRegion> toChange = new ArrayList<>();
        for (ProtectedRegion r : rgm.getRegions().values()) {
            if (ProtectionStones.isPSRegion(r)) {
                PSRegion pr = PSRegion.fromWGRegion(w, r);
                if (pr.getType().equals(fromBlock)) toChange.add(pr);

                if (pr instanceof PSGroupRegion) {
                    for (PSMergedRegion psmr : ((PSGroupRegion) pr).getMergedRegions()) {
                        if (psmr.getType().equals(fromBlock)) toChange.add(psmr);
                    }
                }
            }
        }

        if (toChange.isEmpty()) {
            SchedulerUtil.sendMessage(p, ChatColor.GRAY + "Done!");
            return;
        }
        AtomicInteger remaining = new AtomicInteger(toChange.size());
        for (PSRegion region : toChange) {
            SchedulerUtil.executeForLocation(region.getProtectBlockLocation(), () -> {
                SchedulerUtil.sendMessage(p, ChatColor.GRAY + "Changing " + region.getId() + "...");
                region.setType(ProtectionStones.getBlockOptions(toBlock));
                if (remaining.decrementAndGet() == 0) SchedulerUtil.sendMessage(p, ChatColor.GRAY + "Done!");
            });
        }

        });
        return true;
    }
}
