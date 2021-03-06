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

package me.lucko.luckperms.api.sponge.collections;

import lombok.Getter;
import lombok.NonNull;
import me.lucko.luckperms.api.sponge.LuckPermsService;
import me.lucko.luckperms.api.sponge.LuckPermsUserSubject;
import me.lucko.luckperms.api.sponge.simple.SimpleCollection;
import me.lucko.luckperms.commands.Util;
import me.lucko.luckperms.users.User;
import me.lucko.luckperms.users.UserIdentifier;
import me.lucko.luckperms.users.UserManager;
import org.spongepowered.api.service.context.Context;
import org.spongepowered.api.service.permission.PermissionService;
import org.spongepowered.api.service.permission.Subject;
import org.spongepowered.api.service.permission.SubjectCollection;
import org.spongepowered.api.service.permission.SubjectData;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Manages low level Subject instances for the PermissionService.
 * Most calls are cached.
 */
public class UserCollection implements SubjectCollection {
    private final LuckPermsService service;
    private final UserManager manager;
    private final SimpleCollection fallback;

    @Getter
    private final Map<UUID, LuckPermsUserSubject> users = new ConcurrentHashMap<>();

    public UserCollection(LuckPermsService service, UserManager manager) {
        this.service = service;
        this.manager = manager;
        this.fallback = new SimpleCollection(service, "fallback-users");
    }

    @Override
    public String getIdentifier() {
        return PermissionService.SUBJECTS_USER;
    }

    /**
     * Load a user into this manager
     * @param uuid the external uuid of the user
     */
    public synchronized void load(UUID uuid) {
        UUID internal = service.getPlugin().getUuidCache().getUUID(uuid);
        if (!manager.isLoaded(UserIdentifier.of(internal, null))) {
            return; // Not loaded at a higher level
        }

        if (users.containsKey(internal)) {
            return; // Already loaded
        }

        User user = manager.get(internal);
        LuckPermsUserSubject subject = LuckPermsUserSubject.wrapUser(user, service);
        subject.calculateActivePermissions(true); // Pre-process some of their permissions

        users.put(internal, subject);
    }

    /**
     * Unload a user from this manager
     * @param uuid the internal uuid of the user
     */
    public void unload(UUID uuid) {
        if (users.containsKey(uuid)) {
            users.remove(uuid).deprovision();
        }
    }

    @Override
    public Subject get(@NonNull String id) {
        final UUID uuid = Util.parseUuid(id);
        if (uuid != null) {
            UUID internal = service.getPlugin().getUuidCache().getUUID(uuid);
            if (users.containsKey(internal)) {
                return users.get(internal);
            }

        } else {
            for (LuckPermsUserSubject subject : users.values()) {
                if (subject.getUser().getName().equals(id)) {
                    return subject;
                }
            }
        }

        if (service.getPlugin().getConfiguration().isDebugPermissionChecks()) {
            service.getPlugin().getLog().warn("Couldn't get user subject for: " + id);
        }

        // Fallback to the other collection. This Subject instance will never be persisted.
        return fallback.get(id);
    }

    @Override
    public boolean hasRegistered(@NonNull String id) {
        final UUID uuid = Util.parseUuid(id);
        if (uuid != null) {
            UUID internal = service.getPlugin().getUuidCache().getUUID(uuid);
            if (users.containsKey(internal)) {
                return true;
            }

        } else {
            for (LuckPermsUserSubject subject : users.values()) {
                if (subject.getUser().getName().equals(id)) {
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public Iterable<Subject> getAllSubjects() {
        return users.values().stream().collect(Collectors.toList());
    }

    @Override
    public Map<Subject, Boolean> getAllWithPermission(@NonNull String id) {
        return getAllWithPermission(SubjectData.GLOBAL_CONTEXT, id);
    }

    @Override
    public Map<Subject, Boolean> getAllWithPermission(@NonNull Set<Context> contexts, @NonNull String node) {
        return users.values().stream()
                .filter(sub -> sub.isPermissionSet(contexts, node))
                .collect(Collectors.toMap(sub -> sub, sub -> sub.getPermissionValue(contexts, node).asBoolean()));
    }

    @Override
    public Subject getDefaults() {
        return service.getDefaultSubjects().get(getIdentifier());
    }
}
