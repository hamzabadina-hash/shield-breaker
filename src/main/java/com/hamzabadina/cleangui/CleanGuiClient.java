package com.hamzabadina.cleangui;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class CleanGuiClient implements ClientModInitializer {

    private static KeyBinding toggleKey;
    private static boolean modEnabled = false;
    private static boolean wasPressed = false;

    private static final Set<UUID> alreadyHit = new HashSet<>();

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
                    net.minecraft.text.Text.literal("§7[§bClean GUI §7v1.2.0] §fShield Breaker: "
                        + (modEnabled ? "§aON" : "§cOFF")
                        + " §8| Credits: §6SG_Mafia_"),
                    true
                );
            }
            if (!toggleKey.isPressed()) wasPressed = false;

            // Handle swap back after 50ms
            if (swapPending && System.currentTimeMillis() >= swapBackTime) {
                if (originalSlotPending >= 0 && originalSlotPending <= 8) {
                    setSlot(client, originalSlotPending);
                }
                swapPending = false;
                originalSlotPending = -1;
                swapBackTime = -1;
            }

            if (!modEnabled) return;
            if (swapPending) return;

            ClientPlayerEntity player = client.player;

            // Find axe in hotbar
            int axeSlot = -1;
            for (int i = 0; i < 9; i++) {
                if (player.getInventory().getStack(i).getItem() instanceof AxeItem) {
                    axeSlot = i;
                    break;
                }
            }
            if (axeSlot == -1) return;

            int currentSlot = player.getInventory().selectedSlot;
            if (currentSlot == axeSlot) return;

            // Player look direction
            Vec3d eyePos = player.getEyePos();
            Vec3d lookVec = player.getRotationVec(1.0f);

            List<LivingEntity> nearby = client.world.getEntitiesByClass(
                LivingEntity.class,
                player.getBoundingBox().expand(3.5),
                e -> e != player && e.isAlive()
            );

            for (LivingEntity enemy : nearby) {
                UUID id = enemy.getUuid();

                // Check if looking at enemy
                Vec3d toEnemy = enemy.getEyePos().subtract(eyePos).normalize();
                double dot = lookVec.dotProduct(toEnemy);
                boolean isLookingAt = dot > 0.97;

                boolean isBlocking =
                    (enemy.getStackInHand(Hand.MAIN_HAND).isOf(Items.SHIELD) ||
                     enemy.getStackInHand(Hand.OFF_HAND).isOf(Items.SHIELD))
                    && enemy.isBlocking();

                if (isBlocking && isLookingAt) {
                    if (!alreadyHit.contains(id)) {
                        alreadyHit.add(id);

                        final int finalAxeSlot = axeSlot;
                        final int finalOriginalSlot = currentSlot;

                        // Step 1: swap to axe via vanilla packet (works on any server)
                        setSlot(client, finalAxeSlot);

                        // Step 2: attack using vanilla attack key
                        KeyBinding.onKeyPressed(client.options.attackKey.getDefaultKey());

                        // Step 3: swap back after 50ms
                        swapPending = true;
                        originalSlotPending = finalOriginalSlot;
                        swapBackTime = System.currentTimeMillis() + 50;
                    }
                } else {
                    alreadyHit.remove(id);
                }
            }
        });
    }

    // Sends a vanilla slot change packet — works on ANY server
    private void setSlot(MinecraftClient client, int slot) {
        client.player.getInventory().selectedSlot = slot;
        client.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
    }
}
