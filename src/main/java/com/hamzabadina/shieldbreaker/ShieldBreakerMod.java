package com.hamzabadina.shieldbreaker;

import com.hamzabadina.shieldbreaker.network.SwapAndHitPacket;
import net.fabricmc.api.ModInitializer;

public class ShieldBreakerMod implements ModInitializer {
    public static final String MOD_ID = "shieldbreaker";

    @Override
    public void onInitialize() {
        SwapAndHitPacket.registerReceiver();
    }
}
