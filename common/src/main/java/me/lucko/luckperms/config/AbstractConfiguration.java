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

package me.lucko.luckperms.config;

import com.google.common.collect.ImmutableMap;
import lombok.AccessLevel;
import lombok.Getter;
import me.lucko.luckperms.LuckPermsPlugin;
import me.lucko.luckperms.constants.Patterns;
import me.lucko.luckperms.storage.DatastoreConfiguration;

import java.util.Collections;
import java.util.Map;

/**
 * A thread-safe config abstraction
 * @param <T> the plugin type
 */
@Getter
public abstract class AbstractConfiguration<T extends LuckPermsPlugin> implements LPConfiguration {

    @Getter(AccessLevel.PROTECTED)
    private final T plugin;

    // Values
    private String server;
    private int syncTime;
    private String defaultGroupNode;
    private String defaultGroupName;
    private boolean includingGlobalPerms;
    private boolean includingGlobalWorldPerms;
    private boolean applyingGlobalGroups;
    private boolean applyingGlobalWorldGroups;
    private boolean onlineMode;
    private boolean applyingWildcards;
    private boolean applyingRegex;
    private boolean applyingShorthand;
    private boolean logNotify;
    private boolean debugPermissionChecks;
    private boolean opsEnabled;
    private boolean commandsAllowOp;
    private boolean autoOp;
    private String vaultServer;
    private boolean vaultIncludingGlobal;
    private boolean vaultIgnoreWorld;
    private Map<String, String> worldRewrites;
    private Map<String, String> groupNameRewrites;
    private DatastoreConfiguration databaseValues;
    private String storageMethod;
    private boolean splitStorage;
    private Map<String, String> splitStorageOptions;

    public AbstractConfiguration(T plugin, String defaultServerName, boolean defaultIncludeGlobal, String defaultStorage) {
        this.plugin = plugin;
        init();
        load(defaultServerName, defaultIncludeGlobal, defaultStorage);
    }

    protected abstract void init();
    protected abstract String getString(String path, String def);
    protected abstract int getInt(String path, int def);
    protected abstract boolean getBoolean(String path, boolean def);
    protected abstract Map<String, String> getMap(String path, Map<String, String> def);
    
    public void load(String defaultServerName, boolean defaultIncludeGlobal, String defaultStorage) {
        server = getString("server", defaultServerName);
        syncTime = getInt("data.sync-minutes", 3);
        defaultGroupNode = "group.default"; // constant since 2.6
        defaultGroupName = "default"; // constant since 2.6
        includingGlobalPerms = getBoolean("include-global", defaultIncludeGlobal);
        includingGlobalWorldPerms = getBoolean("include-global-world", true);
        applyingGlobalGroups = getBoolean("apply-global-groups", true);
        applyingGlobalWorldGroups = getBoolean("apply-global-world-groups", true);
        onlineMode = getBoolean("online-mode", true);
        applyingWildcards = getBoolean("apply-wildcards", true);
        applyingRegex = getBoolean("apply-regex", true);
        applyingShorthand = getBoolean("apply-shorthand", true);
        logNotify = getBoolean("log-notify", true);
        debugPermissionChecks = getBoolean("debug-permission-checks", false);
        autoOp = getBoolean("auto-op", false);
        opsEnabled = !isAutoOp() && getBoolean("enable-ops", true);
        commandsAllowOp = getBoolean("commands-allow-op", true);
        vaultServer = getString("vault-server", "global");
        vaultIncludingGlobal = getBoolean("vault-include-global", true);
        vaultIgnoreWorld = getBoolean("vault-ignore-world", false);
        worldRewrites = ImmutableMap.copyOf(getMap("world-rewrite", Collections.emptyMap()));
        groupNameRewrites = ImmutableMap.copyOf(getMap("group-name-rewrite", Collections.emptyMap()));
        databaseValues = new DatastoreConfiguration(
                getString("data.address", null),
                getString("data.database", null),
                getString("data.username", null),
                getString("data.password", null)
        );
        storageMethod = getString("storage-method", defaultStorage);
        splitStorage = getBoolean("split-storage.enabled", false);
        splitStorageOptions = ImmutableMap.<String, String>builder()
                .put("user", getString("split-storage.methods.user", defaultStorage))
                .put("group", getString("split-storage.methods.group", defaultStorage))
                .put("track", getString("split-storage.methods.track", defaultStorage))
                .put("uuid", getString("split-storage.methods.uuid", defaultStorage))
                .put("log", getString("split-storage.methods.log", defaultStorage))
                .build();
        
        if (Patterns.NON_ALPHA_NUMERIC.matcher(getServer()).find()) {
            plugin.getLog().severe("Server name defined in config.yml contains invalid characters. Server names can " +
                    "only contain alphanumeric characters.\nDefined server name '" + getServer() + "' will be replaced with '" +
                    defaultServerName + "' (the default)");
            server = defaultServerName;
        }
    }
}
