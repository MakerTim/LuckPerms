/*
 * Copyright (c) 2016 Lucko (Luck) <luck@lucko.me>
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */

package me.lucko.luckperms.users;

import lombok.Getter;
import lombok.Setter;
import me.lucko.luckperms.LPBukkitPlugin;
import me.lucko.luckperms.api.Contexts;
import me.lucko.luckperms.api.event.events.UserPermissionRefreshEvent;
import me.lucko.luckperms.api.implementation.internal.UserLink;
import me.lucko.luckperms.inject.LPPermissible;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permissible;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BukkitUser extends User {
    private final LPBukkitPlugin plugin;

    @Getter
    @Setter
    private LPPermissible permissible = null;

    @Getter
    @Setter
    private Map<String, Boolean> loginPreProcess = null;

    BukkitUser(UUID uuid, LPBukkitPlugin plugin) {
        super(uuid, plugin);
        this.plugin = plugin;
    }

    BukkitUser(UUID uuid, String username, LPBukkitPlugin plugin) {
        super(uuid, username, plugin);
        this.plugin = plugin;
    }

    public boolean isOp() {
        return permissible != null && permissible.isOp();
    }

    @SuppressWarnings("unchecked")
    @Override
    public synchronized void refreshPermissions() {
        LPPermissible permissible = getPermissible();
        if (permissible == null) {
            return;
        }

        // Calculate the permissions that should be applied. This is done async, who cares about how long it takes or how often it's done.
        Map<String, Boolean> toApply = exportNodes(
                new Contexts(
                        plugin.getContextManager().giveApplicableContext((Player) permissible.getParent(), new HashMap<>()),
                        plugin.getConfiguration().isIncludingGlobalPerms(),
                        plugin.getConfiguration().isIncludingGlobalWorldPerms(),
                        true,
                        plugin.getConfiguration().isApplyingGlobalGroups(),
                        plugin.getConfiguration().isApplyingGlobalWorldGroups()
                ),
                Collections.emptyList(),
                true
        );

        try {
            Map<String, Boolean> existing = permissible.getLuckPermsPermissions();

            boolean different = false;
            if (toApply.size() != existing.size()) {
                different = true;
            } else {
                for (Map.Entry<String, Boolean> e : existing.entrySet()) {
                    if (toApply.containsKey(e.getKey()) && toApply.get(e.getKey()) == e.getValue()) {
                        continue;
                    }
                    different = true;
                    break;
                }
            }

            if (!different) return;

            existing.clear();
            permissible.invalidateCache();
            existing.putAll(toApply);

            if (plugin.getConfiguration().isAutoOp()) {
                boolean op = false;

                for (Map.Entry<String, Boolean> e : toApply.entrySet()) {
                    if (e.getKey().equalsIgnoreCase("luckperms.autoop") && e.getValue()) {
                        op = true;
                        break;
                    }
                }

                final boolean finalOp = op;
                if (permissible.isOp() != op) {
                    final Permissible parent = permissible.getParent();
                    plugin.doSync(() -> parent.setOp(finalOp));
                }
            }

            plugin.getApiProvider().fireEventAsync(new UserPermissionRefreshEvent(new UserLink(this)));

        } catch (Exception e) {
            e.printStackTrace();
        }

        if (plugin.getVaultHook() != null && plugin.getVaultHook().isHooked()) {
            plugin.getVaultHook().getPermissionHook().getVaultUserManager().setupUser(this);
        }
    }
}
