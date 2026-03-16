package com.hamzabadina.cleangui;

import com.hamzabadina.cleangui.network.SwapAndHitPacket;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

public class CleanGuiMod implements ModInitializer {
    public static final String MOD_ID = "cleangui";

    @Override
    public void onInitialize() {
        SwapAndHitPacket.registerReceiver();

        // Log when a player joins so server knows mod is active
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            System.out.println("[CleanGUI] " + handler.player.getName().getString() + " joined with Clean GUI mod.");
        });
    }
}
