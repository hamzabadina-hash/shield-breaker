package com.hamzabadina.cleangui;

import com.hamzabadina.cleangui.network.SwapAndHitPacket;
import net.fabricmc.api.ModInitializer;

public class CleanGuiMod implements ModInitializer {
    public static final String MOD_ID = "cleangui";

    @Override
    public void onInitialize() {
        SwapAndHitPacket.registerReceiver();
    }
}
