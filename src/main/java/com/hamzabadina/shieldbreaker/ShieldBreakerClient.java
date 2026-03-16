package com.hamzabadina.shieldbreaker;

import com.hamzabadina.shieldbreaker.network.SwapAndHitPacket;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import org.lwjgl.glfw.GLFW;

public class ShieldBreakerClient implements ClientModInitializer {

    private static KeyBinding toggleKey;
    private static boolean modEnabled = false;
    private static boolean wasPressed = false;

    @Override
    public void onInitializeClient() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.shieldbreaker.toggle",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            "category.shieldbreaker"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;

            if (toggleKey.isPressed() && !wasPressed) {
                modEnabled = !modEnabled;
                wasPressed = true;
                client.player.sendMessage(
                    net.minecraft.text.Text.literal("Shield Breaker: " + (modEnabled ? "§aON" : "§cOFF")),
                    true
                );
            }
            if (!toggleKey.isPressed()) wasPressed = false;

            if (!modEnabled) return;

            int axeSlot = -1;
            for (int i = 0; i < 9; i++) {
                if (client.player.getInventory().getStack(i).getItem() instanceof AxeItem) {
                    axeSlot = i;
                    break;
                }
            }
            if (axeSlot == -1) return;

            int currentSlot = client.player.getInventory().selectedSlot;
            if (currentSlot == axeSlot) return;

            boolean enemyHasShield = client.world.getEntitiesByClass(
                LivingEntity.class,
                client.player.getBoundingBox().expand(3.5),
                e -> e != client.player && e.isAlive()
            ).stream().anyMatch(e ->
                e.getStackInHand(Hand.MAIN_HAND).isOf(Items.SHIELD) ||
                e.getStackInHand(Hand.OFF_HAND).isOf(Items.SHIELD)
            );

            if (!enemyHasShield) return;

            final int finalAxeSlot = axeSlot;
            final int finalOriginalSlot = currentSlot;
            ClientPlayNetworking.send(new SwapAndHitPacket.Payload(finalAxeSlot, finalOriginalSlot));
        });
    }
}
