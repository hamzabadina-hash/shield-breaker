package com.hamzabadina.shieldbreaker;

import com.hamzabadina.shieldbreaker.network.SwapAndHitPacket;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class ShieldBreakerClient implements ClientModInitializer {

    private static KeyBinding toggleKey;
    private static boolean modEnabled = false;
    private static boolean wasPressed = false;

    private static final Set<UUID> alreadyHit = new HashSet<>();

    // Swap state machine
    private static boolean swapPending = false;
    private static int originalSlotPending = -1;
    private static long swapBackTime = -1;

    @Override
    public void onInitializeClient() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.cleangui.toggle",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            "category.cleangui"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;

            // Toggle on/off
            if (toggleKey.isPressed() && !wasPressed) {
                modEnabled = !modEnabled;
                wasPressed = true;
                client.player.sendMessage(
                    net.minecraft.text.Text.literal("§7[§bClean GUI §7v1.2.0] §fShield Breaker: " + (modEnabled ? "§aON" : "§cOFF") + " §8| Credits: §6SG_Mafia_"),
                    true
                );
            }
            if (!toggleKey.isPressed()) wasPressed = false;

            // Handle swap back after 100ms delay
            if (swapPending && System.currentTimeMillis() >= swapBackTime) {
                if (originalSlotPending >= 0 && originalSlotPending <= 8) {
                    client.player.getInventory().selectedSlot = originalSlotPending;
                }
                swapPending = false;
                originalSlotPending = -1;
                swapBackTime = -1;
            }

            if (!modEnabled) return;
            if (swapPending) return; // Wait until swap back is done

            // Find axe in hotbar
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

            // Get nearby enemies
            java.util.List<LivingEntity> nearby = client.world.getEntitiesByClass(
                LivingEntity.class,
                client.player.getBoundingBox().expand(3.5),
                e -> e != client.player && e.isAlive()
            );

            for (LivingEntity enemy : nearby) {
                UUID id = enemy.getUuid();

                boolean isBlocking =
                    (enemy.getStackInHand(Hand.MAIN_HAND).isOf(Items.SHIELD) ||
                     enemy.getStackInHand(Hand.OFF_HAND).isOf(Items.SHIELD))
                    && enemy.isBlocking();

                if (isBlocking) {
                    if (!alreadyHit.contains(id)) {
                        alreadyHit.add(id);

                        // Step 1: visually swap to axe on client
                        client.player.getInventory().selectedSlot = axeSlot;

                        // Step 2: send packet to server to hit
                        final int finalAxeSlot = axeSlot;
                        final int finalOriginalSlot = currentSlot;
                        ClientPlayNetworking.send(new SwapAndHitPacket.Payload(finalAxeSlot, finalOriginalSlot));

                        // Step 3: schedule swap back after 100ms
                        swapPending = true;
                        originalSlotPending = finalOriginalSlot;
                        swapBackTime = System.currentTimeMillis() + 100;
                    }
                } else {
                    alreadyHit.remove(id);
                }
            }
        });
    }
}
