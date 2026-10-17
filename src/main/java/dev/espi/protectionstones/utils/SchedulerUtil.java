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

package dev.espi.protectionstones.utils;

import dev.espi.protectionstones.ProtectionStones;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.concurrent.TimeUnit;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Small set of scheduler helpers shared by Paper and Folia.
 * Paper implements these schedulers too, so callers do not need a legacy scheduler branch.
 */
public final class SchedulerUtil {
    private SchedulerUtil() {}

    private static ProtectionStones plugin() {
        return ProtectionStones.getInstance();
    }

    public static boolean executeForEntity(Entity entity, Runnable task) {
        return entity.getScheduler().execute(plugin(), task, () -> {}, 1L);
    }

    public static boolean executeForEntity(Entity entity, Runnable task, Runnable retired) {
        return entity.getScheduler().execute(plugin(), task, retired, 1L);
    }

    public static void executeForSender(CommandSender sender, Runnable task) {
        if (sender instanceof Entity entity) executeForEntity(entity, task);
        else executeGlobally(task);
    }

    public static void sendMessage(CommandSender sender, String message) {
        executeForSender(sender, () -> sender.sendMessage(message));
    }

    public static void sendToPlayer(UUID playerId, String message) {
        executeForPlayerId(playerId, player -> player.sendMessage(message));
    }

    public static void executeForPlayerId(UUID playerId, Consumer<Player> action) {
        if (playerId == null) return;
        executeGlobally(() -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) executeForEntity(player, () -> action.accept(player));
        });
    }

    public static ScheduledTask laterForEntity(Entity entity, Runnable task, long delayTicks) {
        return entity.getScheduler().runDelayed(plugin(), scheduledTask -> task.run(), () -> {}, delayTicks);
    }

    public static ScheduledTask repeatForEntity(Entity entity, Consumer<ScheduledTask> task, long delayTicks, long periodTicks) {
        return entity.getScheduler().runAtFixedRate(plugin(), task, () -> {}, delayTicks, periodTicks);
    }

    public static void executeForLocation(Location location, Runnable task) {
        Bukkit.getRegionScheduler().execute(plugin(), location, task);
    }

    public static ScheduledTask laterForLocation(Location location, Runnable task, long delayTicks) {
        return Bukkit.getRegionScheduler().runDelayed(plugin(), location, scheduledTask -> task.run(), delayTicks);
    }

    public static ScheduledTask repeatForLocation(Location location, Consumer<ScheduledTask> task, long delayTicks, long periodTicks) {
        return Bukkit.getRegionScheduler().runAtFixedRate(plugin(), location, task, delayTicks, periodTicks);
    }

    public static void executeGlobally(Runnable task) {
        Bukkit.getGlobalRegionScheduler().execute(plugin(), task);
    }

    public static ScheduledTask laterGlobally(Runnable task, long delayTicks) {
        return Bukkit.getGlobalRegionScheduler().runDelayed(plugin(), scheduledTask -> task.run(), delayTicks);
    }

    public static ScheduledTask repeatGlobally(Consumer<ScheduledTask> task, long delayTicks, long periodTicks) {
        return Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin(), task, delayTicks, periodTicks);
    }

    public static ScheduledTask runAsync(Runnable task) {
        return Bukkit.getAsyncScheduler().runNow(plugin(), scheduledTask -> task.run());
    }

    public static ScheduledTask laterAsync(Runnable task, long delay, TimeUnit unit) {
        return Bukkit.getAsyncScheduler().runDelayed(plugin(), scheduledTask -> task.run(), delay, unit);
    }

    public static ScheduledTask repeatAsync(Consumer<ScheduledTask> task, long delay, long period, TimeUnit unit) {
        return Bukkit.getAsyncScheduler().runAtFixedRate(plugin(), task, delay, period, unit);
    }
}
