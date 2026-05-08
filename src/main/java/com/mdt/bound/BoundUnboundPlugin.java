package com.mdt.bound;

import arc.util.CommandHandler;
import arc.util.Log;
import mindustry.gen.Player;
import mindustry.mod.Plugin;

public final class BoundUnboundPlugin extends Plugin {
    @Override
    public void init() {
        Log.info("MDT 绑定与未绑定 loaded.");
        Log.info("配置目录建议: config/mods/config/mdt-bound-unbound");
    }

    @Override
    public void registerServerCommands(CommandHandler handler) {
        handler.register("bind-state-check", "<uuid>", "按 UUID 主动检查绑定状态。", args -> {
            Log.info("MDT 绑定与未绑定 命令占位已触发: bind-state-check");
        });

        handler.register("bind-state-set", "<comid> <true|false>", "手动设置某个 com id 的绑定状态。", args -> {
            Log.info("MDT 绑定与未绑定 命令占位已触发: bind-state-set");
        });

        handler.register("bind-state-reload", "重新加载绑定识别配置。", args -> {
            Log.info("MDT 绑定与未绑定 命令占位已触发: bind-state-reload");
        });

    }

    @Override
    public void registerClientCommands(CommandHandler handler) {
        handler.<Player>register("bindstate", "查看自己的绑定识别状态。", (args, player) -> {
            player.sendMessage("[accent]MDT 绑定与未绑定[] 命令占位已触发: bindstate");
        });

    }
}
