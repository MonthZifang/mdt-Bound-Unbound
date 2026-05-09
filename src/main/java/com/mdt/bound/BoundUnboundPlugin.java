package com.mdt.bound;

import arc.Events;
import arc.util.CommandHandler;
import arc.util.Log;
import arc.util.Strings;
import arc.util.Timer;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import mindustry.Vars;
import mindustry.game.EventType.PlayerJoin;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.mod.Plugin;

public final class BoundUnboundPlugin extends Plugin {
    private static final String CONFIG_DIR_NAME = "mdt-bound-unbound";
    private static final String CONFIG_FILE_NAME = "bound-unbound.properties";

    private static volatile BoundUnboundPlugin instance;

    private volatile Config config;

    @Override
    public void init() {
        try {
            instance = this;
            ensureDefaultResource();
            reloadConfig();
            Events.on(PlayerJoin.class, event -> {
                if (config.autoCheckOnJoin) {
                    Timer.schedule(() -> refreshPlayer(event.player), 0.4f);
                }
            });
            Log.info("MDT Bound/Unbound loaded.");
            Log.info("Config directory: @", resolveDataRoot().getAbsolutePath());
        } catch (IOException exception) {
            throw new RuntimeException("Failed to initialize MDT Bound/Unbound.", exception);
        }
    }

    public static void refreshPlayerStateByUuid(String uuid) {
        BoundUnboundPlugin plugin = instance;
        if (plugin == null || uuid == null || uuid.trim().isEmpty()) {
            return;
        }
        Player player = Groups.player.find(p -> uuid.equalsIgnoreCase(plugin.resolveUuid(p)));
        if (player != null) {
            plugin.refreshPlayer(player);
        }
    }

    public static void refreshAllPlayerStates() {
        BoundUnboundPlugin plugin = instance;
        if (plugin != null) {
            plugin.refreshAllPlayers();
        }
    }

    @Override
    public void registerServerCommands(CommandHandler handler) {
        handler.register("bind-state-check", "<uuid|comid|player>", "Check one binding state.", args -> {
            Record record = resolveRecord(args[0], true);
            if (record == null) {
                Log.info("Binding record not found: @", args[0]);
                return;
            }
            printRecord(record);
        });

        handler.register("bind-state-set", "<comid> <true|false>", "Manually set one binding state.", args -> {
            String comId = args[0].trim().toUpperCase();
            boolean bound = Boolean.parseBoolean(args[1]);
            Record record = getRecordByComId(comId, true);
            if (record == null) {
                Log.info("Failed to create binding record: @", comId);
                return;
            }
            record.bound = bound;
            record.source = "manual";
            record.bindTime = nowText();
            saveRecord(record);
            refreshAllPlayers();
            Log.info("Binding state updated. comid=@ bound=@", comId, bound);
            printRecord(record);
        });

        handler.register("bind-state-reload", "Reload binding state config.", args -> {
            try {
                reloadConfig();
                refreshAllPlayers();
                Log.info("Bound/Unbound config reloaded. profileByComid=@ profileByUuid=@ display=@",
                    config.queryProfileByComid, config.queryProfileByUuid, config.displayEnabled);
            } catch (IOException exception) {
                Log.err("Failed to reload Bound/Unbound config: @", exception.getMessage());
            }
        });
    }

    @Override
    public void registerClientCommands(CommandHandler handler) {
        handler.<Player>register("bindstate", "Show your current binding state.", (args, player) -> {
            Record record = getRecordForPlayer(player, true);
            if (record == null) {
                player.sendMessage("[scarlet]Failed to read your binding state.");
                return;
            }
            player.sendMessage(formatRecord(record));
        });
    }

    private void refreshAllPlayers() {
        for (Player player : Groups.player) {
            refreshPlayer(player);
        }
    }

    private void refreshPlayer(Player player) {
        if (player == null) {
            return;
        }
        Record record = getRecordForPlayer(player, true);
        if (record == null || !config.displayEnabled) {
            return;
        }
        applyPrefix(player, record.bound);
    }

    private void applyPrefix(Player player, boolean bound) {
        String prefix = bound ? config.boundText : config.unboundText;
        String current = player.name == null ? "" : player.name;
        String stripped = stripStatusPrefix(current);
        player.name = config.showPrefixBeforeName ? prefix + stripped : stripped + prefix;
    }

    private String stripStatusPrefix(String text) {
        if (text.startsWith(config.boundText)) {
            return text.substring(config.boundText.length());
        }
        if (text.startsWith(config.unboundText)) {
            return text.substring(config.unboundText.length());
        }
        if (text.endsWith(config.boundText)) {
            return text.substring(0, text.length() - config.boundText.length());
        }
        if (text.endsWith(config.unboundText)) {
            return text.substring(0, text.length() - config.unboundText.length());
        }
        return text;
    }

    private Record resolveRecord(String value, boolean createIfMissing) {
        Player player = findPlayer(value);
        if (player != null) {
            return getRecordForPlayer(player, createIfMissing);
        }
        if (value.length() <= 8 && value.matches("[A-Za-z0-9]+")) {
            return getRecordByComId(value.toUpperCase(), createIfMissing);
        }
        String comId = resolveComIdByUuid(value);
        if (comId != null) {
            return getRecordByComId(comId, createIfMissing);
        }
        return getRecordByUuid(value, createIfMissing);
    }

    private Record getRecordForPlayer(Player player, boolean createIfMissing) {
        String uuid = resolveUuid(player);
        if (uuid == null) {
            return null;
        }
        String comId = resolveComIdByUuid(uuid);
        if (comId != null && !comId.trim().isEmpty()) {
            Record record = getRecordByComId(comId, createIfMissing);
            if (record != null) {
                return record;
            }
        }
        return getRecordByUuid(uuid, createIfMissing);
    }

    private Record getRecordByComId(String comId, boolean createIfMissing) {
        Map<String, String> object = queryBindingObject(config.queryProfileByComid, comId);
        if (object.isEmpty()) {
            object = listDataObject(config.bindListName, comId);
        }
        if (object.isEmpty() && !createIfMissing) {
            return null;
        }
        Record record = createRecord(comId, object);
        if (createIfMissing && object.isEmpty()) {
            saveRecord(record);
        }
        return record;
    }

    private Record getRecordByUuid(String uuid, boolean createIfMissing) {
        Map<String, String> object = queryBindingObject(config.queryProfileByUuid, uuid);
        if (object.isEmpty() && !createIfMissing) {
            return null;
        }
        String comId = firstNonBlank(object.get(config.comIdField), resolveComIdByUuid(uuid), uuid);
        Record record = createRecord(comId, object);
        if (createIfMissing && !uuid.equalsIgnoreCase(comId) && listDataObject(config.bindListName, comId).isEmpty()) {
            saveRecord(record);
        }
        return record;
    }

    private Record createRecord(String comId, Map<String, String> object) {
        Record record = new Record();
        record.comId = comId;
        record.bound = Boolean.parseBoolean(object.getOrDefault(config.boundField, "false"));
        record.source = object.getOrDefault(config.sourceField, record.bound ? "query" : "default");
        record.bindTime = object.getOrDefault(config.timeField, record.bound ? nowText() : "-");
        return record;
    }

    private void saveRecord(Record record) {
        LinkedHashMap<String, String> values = new LinkedHashMap<String, String>();
        values.put(config.comIdField, record.comId);
        values.put(config.boundField, Boolean.toString(record.bound));
        values.put(config.sourceField, record.source == null ? "" : record.source);
        values.put(config.timeField, record.bindTime == null ? "" : record.bindTime);
        listDataPutObject(config.bindListName, record.comId, values);
    }

    private void printRecord(Record record) {
        Log.info("comid=@ bound=@ source=@ bindTime=@", record.comId, record.bound, record.source, record.bindTime);
    }

    private String formatRecord(Record record) {
        return "[accent]comid[]: " + record.comId
            + "\n[accent]\u7ed1\u5b9a\u72b6\u6001[]: " + (record.bound ? "\u5df2\u7ed1\u5b9a" : "\u672a\u7ed1\u5b9a")
            + "\n[accent]\u6765\u6e90[]: " + record.source
            + "\n[accent]\u7ed1\u5b9a\u65f6\u95f4[]: " + record.bindTime;
    }

    private Player findPlayer(String value) {
        String normalized = Strings.stripColors(value).trim();
        return Groups.player.find(player ->
            normalized.equalsIgnoreCase(resolveUuid(player))
                || normalized.equalsIgnoreCase(player.plainName())
                || normalized.equalsIgnoreCase(Strings.stripColors(player.name))
        );
    }

    private String resolveComIdByUuid(String uuid) {
        try {
            Object api = resolveSharedService("mdt.jump.api", "com.mdt.jump.api.JumpComIdApi", "com.mdt.jump.JumpComIdPlugin");
            if (api == null) {
                return null;
            }
            Object record = api.getClass().getMethod("getOrCreate", String.class).invoke(api, uuid);
            if (record == null) {
                return null;
            }
            Object value = record.getClass().getMethod("getComId").invoke(record);
            return value == null ? null : value.toString();
        } catch (Exception exception) {
            Log.err("Failed to resolve comid: @", exception.getMessage());
            return null;
        }
    }

    private String resolveUuid(Player player) {
        if (player == null) {
            return null;
        }
        try {
            return player.uuid();
        } catch (Throwable ignored) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> queryBindingObject(String profile, String selector) {
        if (profile == null || profile.trim().isEmpty() || selector == null || selector.trim().isEmpty()) {
            return new LinkedHashMap<String, String>();
        }
        try {
            Object api = resolveSharedService("mdt.listdata.api", "com.mdt.listdata.api.ListDataSystemApi", "com.mdt.listdata.ListDataSystemPlugin");
            Object result = invokeListData(api, "queryProfile", new Class<?>[]{String.class, String.class}, new Object[]{profile, selector});
            return result instanceof Map ? (Map<String, String>) result : new LinkedHashMap<String, String>();
        } catch (Exception exception) {
            Log.warn("Binding profile query failed. profile=@ selector=@ error=@",
                profile, selector, exception.getMessage());
            return new LinkedHashMap<String, String>();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> listDataObject(String listName, String key) {
        try {
            Object api = resolveSharedService("mdt.listdata.api", "com.mdt.listdata.api.ListDataSystemApi", "com.mdt.listdata.ListDataSystemPlugin");
            Object result = invokeListData(api, "getObject", new Class<?>[]{String.class, String.class}, new Object[]{listName, key});
            return result == null ? new LinkedHashMap<String, String>() : (Map<String, String>) result;
        } catch (Exception exception) {
            throw new RuntimeException("Failed to query ListDataSystem.", exception);
        }
    }

    private void listDataPutObject(String listName, String key, Map<String, String> values) {
        try {
            Object api = resolveSharedService("mdt.listdata.api", "com.mdt.listdata.api.ListDataSystemApi", "com.mdt.listdata.ListDataSystemPlugin");
            invokeListData(api, "putObject", new Class<?>[]{String.class, String.class, Map.class}, new Object[]{listName, key, values});
        } catch (Exception exception) {
            throw new RuntimeException("Failed to write ListDataSystem.", exception);
        }
    }

    private void reloadConfig() throws IOException {
        File configFile = new File(resolveDataRoot(), CONFIG_FILE_NAME);
        Properties properties = new Properties();
        InputStreamReader reader = new InputStreamReader(new FileInputStream(configFile), StandardCharsets.UTF_8);
        try {
            properties.load(reader);
        } finally {
            reader.close();
        }
        config = new Config(
            readBoolean(properties, "display.enabled", true),
            readBoolean(properties, "display.prefixBeforeName", true),
            readString(properties, "display.boundText", "[green][\u5df2\u7ed1\u5b9a][]"),
            readString(properties, "display.unboundText", "[scarlet][\u672a\u7ed1\u5b9a][]"),
            readString(properties, "data.bindListName", "player_bind"),
            readString(properties, "data.comidField", "comId"),
            readString(properties, "data.boundField", "bound"),
            readString(properties, "data.sourceField", "bindSource"),
            readString(properties, "data.timeField", "bindTime"),
            readString(properties, "query.profileByComid", "bind-local-by-comid"),
            readString(properties, "query.profileByUuid", "bind-local-by-uuid"),
            readBoolean(properties, "check.autoCheckOnJoin", true)
        );
    }

    private String readString(Properties properties, String key, String fallback) {
        String value = properties.getProperty(key);
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private boolean readBoolean(Properties properties, String key, boolean fallback) {
        String value = properties.getProperty(key);
        return value == null || value.trim().isEmpty() ? fallback : Boolean.parseBoolean(value.trim());
    }

    private void ensureDefaultResource() throws IOException {
        File dataRoot = resolveDataRoot();
        if (!dataRoot.exists() && !dataRoot.mkdirs() && !dataRoot.isDirectory()) {
            throw new IOException("Unable to create config directory: " + dataRoot.getAbsolutePath());
        }
        File configFile = new File(dataRoot, CONFIG_FILE_NAME);
        if (configFile.exists()) {
            return;
        }
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE_NAME);
        if (inputStream == null) {
            throw new IOException("Missing default resource: " + CONFIG_FILE_NAME);
        }
        try {
            Files.copy(inputStream, configFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } finally {
            inputStream.close();
        }
    }

    private File resolveDataRoot() {
        File modsRoot = new File(Vars.dataDirectory.absolutePath(), "mods");
        return new File(new File(modsRoot, "config"), CONFIG_DIR_NAME);
    }

    private String nowText() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }

    private Object resolveSharedService(String serviceKey, String interfaceName, String legacyClassName) throws Exception {
        Object fromHub = tryResolveFromHub(serviceKey);
        if (fromHub != null) {
            return fromHub;
        }

        try {
            Class<?> pluginClass = Class.forName(legacyClassName);
            Method getApi = pluginClass.getMethod("getApi");
            Object api = getApi.invoke(null);
            if (api != null) {
                return api;
            }
        } catch (NoSuchMethodException ignored) {
            // Legacy class may expose only static methods.
        }

        Class<?> legacyClass = Class.forName(legacyClassName);
        if (interfaceName != null && !interfaceName.trim().isEmpty()) {
            try {
                Class.forName(interfaceName);
            } catch (ClassNotFoundException ignored) {
                // Interface is optional for fallback mode.
            }
        }
        return legacyClass;
    }

    private Object tryResolveFromHub(String key) {
        try {
            Class<?> hub = Class.forName("mdt.ServeMdtPlugin");
            return hub.getMethod("getSharedService", String.class).invoke(null, key);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Object invokeListData(Object target, String methodName, Class<?>[] parameterTypes, Object[] args) throws Exception {
        if (target instanceof Class<?>) {
            Method method = ((Class<?>) target).getMethod(methodName, parameterTypes);
            return method.invoke(null, args);
        }
        Method method = target.getClass().getMethod(methodName, parameterTypes);
        return method.invoke(target, args);
    }

    private static final class Config {
        private final boolean displayEnabled;
        private final boolean showPrefixBeforeName;
        private final String boundText;
        private final String unboundText;
        private final String bindListName;
        private final String comIdField;
        private final String boundField;
        private final String sourceField;
        private final String timeField;
        private final String queryProfileByComid;
        private final String queryProfileByUuid;
        private final boolean autoCheckOnJoin;

        private Config(
            boolean displayEnabled,
            boolean showPrefixBeforeName,
            String boundText,
            String unboundText,
            String bindListName,
            String comIdField,
            String boundField,
            String sourceField,
            String timeField,
            String queryProfileByComid,
            String queryProfileByUuid,
            boolean autoCheckOnJoin
        ) {
            this.displayEnabled = displayEnabled;
            this.showPrefixBeforeName = showPrefixBeforeName;
            this.boundText = boundText;
            this.unboundText = unboundText;
            this.bindListName = bindListName;
            this.comIdField = comIdField;
            this.boundField = boundField;
            this.sourceField = sourceField;
            this.timeField = timeField;
            this.queryProfileByComid = queryProfileByComid;
            this.queryProfileByUuid = queryProfileByUuid;
            this.autoCheckOnJoin = autoCheckOnJoin;
        }
    }

    private static final class Record {
        private String comId;
        private boolean bound;
        private String source;
        private String bindTime;
    }
}
