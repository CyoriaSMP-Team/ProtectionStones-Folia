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

import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.util.profile.Profile;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.UUID;

public class UUIDCache {
    private static final ConcurrentMap<UUID, String> uuidToName = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, UUID> nameToUUID = new ConcurrentHashMap<>();
    private static final ConcurrentMap<UUID, String> onlinePlayers = new ConcurrentHashMap<>();
    private static final ConcurrentMap<UUID, String> onlinePlayerNames = new ConcurrentHashMap<>();
    private static final ConcurrentMap<UUID, java.util.List<String>> effectivePermissions = new ConcurrentHashMap<>();

    public static void refreshOnlinePlayers() {
        onlinePlayers.clear();
        onlinePlayerNames.clear();
        for (Player player : Bukkit.getOnlinePlayers()) {
            SchedulerUtil.executeForEntity(player, () -> setOnline(player));
        }
    }

    public static void setOnline(Player player) {
        onlinePlayers.put(player.getUniqueId(), player.getName());
        onlinePlayerNames.put(player.getUniqueId(), player.getName());
        effectivePermissions.put(player.getUniqueId(), player.getEffectivePermissions().stream()
                .map(PermissionAttachmentInfo::getPermission).toList());
        storeUUIDNamePair(player.getUniqueId(), player.getName());
    }

    public static void setOffline(UUID uuid) {
        if (uuid != null) {
            onlinePlayers.remove(uuid);
            onlinePlayerNames.remove(uuid);
            effectivePermissions.remove(uuid);
        }
    }

    public static boolean isOnline(UUID uuid) {
        return uuid != null && onlinePlayers.containsKey(uuid);
    }

    public static java.util.List<String> getOnlinePlayersSnapshot() {
        return new java.util.ArrayList<>(onlinePlayers.values());
    }

    public static java.util.List<String> getOnlinePlayerNames() {
        return new java.util.ArrayList<>(onlinePlayerNames.values());
    }

    public static java.util.List<String> getEffectivePermissions(UUID uuid) {
        return effectivePermissions.get(uuid);
    }

    // toLowerCase for case insensitive search

    public static UUID getUUIDFromName(String name) {
        if (name == null) return null;
        return nameToUUID.get(name.toLowerCase());
    }

    public static String getNameFromUUID(UUID uuid) {
        if (uuid == null) return null;
        return uuidToName.get(uuid);
    }

    public static boolean containsName(String name) {
        if (name == null) return false;
        return nameToUUID.containsKey(name.toLowerCase());
    }

    public static boolean containsUUID(UUID uuid) {
        if (uuid == null) return false;
        return uuidToName.containsKey(uuid);
    }

    public static void storeUUIDNamePair(UUID uuid, String name) {
        if (uuid == null || name == null) return;
        uuidToName.put(uuid, name);
        nameToUUID.put(name.toLowerCase(), uuid);
    }

    public static void removeUUID(UUID uuid) {
        if (uuid == null) return;
        String name = uuidToName.remove(uuid);
        if (name != null) nameToUUID.remove(name.toLowerCase(), uuid);
    }

    public static void removeName(String name) {
        if (name == null) return;
        UUID uuid = nameToUUID.remove(name.toLowerCase());
        if (uuid != null) uuidToName.remove(uuid, name);
    }

    public static void storeWGProfile(UUID uuid, String name) {
        WorldGuard.getInstance().getProfileCache().put(new Profile(uuid, name));
    }
}
