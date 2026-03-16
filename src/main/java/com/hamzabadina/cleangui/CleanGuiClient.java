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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CleanGuiClient implements ClientModInitializer {

    private static KeyBinding toggleKey;
    private static boolean modEnabled = false;
    private static boolean wasPressed = false;

    // Cooldown per enemy: UUID -> timestamp of last hit
    private static final Map<UUID, Long> hitCooldowns = new HashMap<>();
    private static final long HIT_COOLDOWN_MS = 1200; // 1.2s cooldown per enemy before re-triggering

    // State machine for the swap sequence
    private enum SwapState { IDLE, WAITING_TO_HIT, WAITING_TO_SWAP_BACK }
    private static SwapState state = SwapState.IDLE;
    private static int originalSlotPending = -1;
    private static int axeSlotPending = -1;
    private static long actionTime = -1;
    private static UUID targetUUID = null;

    private static long randomDelay() {
        return 40 + (long)(Math.random() * 20); // tightened to 40-60ms
    }

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
            if (client.getNetworkHandler() == null) return;

            // Toggle
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

            long now = System.currentTimeMillis();

            // Clean up stale cooldowns to avoid memory leak
            hitCooldowns.entrySet().removeIf(e -> now - e.getValue() > HIT_COOLDOWN_MS * 2);

            switch (state) {

                case WAITING_TO_HIT -> {
                    if (now >= actionTime) {
                        client.player.getInventory().selectedSlot = axeSlotPending;
                        client.getNetworkHandler().sendPacket(
                            new UpdateSelectedSlotC2SPacket(axeSlotPending)
                        );

                        state = SwapState.WAITING_TO_SWAP_BACK;
                        actionTime = now + randomDelay();
                    }
                }

                case WAITING_TO_SWAP_BACK -> {
                    if (now >= actionTime) {
                        LivingEntity target = getClosestLookedAtEnemy(client);

                        if (target != null) {
                            client.interactionManager.attackEntity(
                                client.player,
                                target
                            );
                            client.player.swingHand(Hand.MAIN_HAND);
                            // Record hit time so cooldown starts from actual hit
                            hitCooldowns.put(target.getUuid(), now);
                        } else {
                            // Target gone — reset their cooldown so we try again immediately
                            if (targetUUID != null) {
                                hitCooldowns.remove(targetUUID);
                            }
                        }

                        // Always swap back
                        client.player.getInventory().selectedSlot = originalSlotPending;
                        client.getNetworkHandler().sendPacket(
                            new UpdateSelectedSlotC2SPacket(originalSlotPending)
                        );

                        state = SwapState.IDLE;
                        originalSlotPending = -1;
                        axeSlotPending = -1;
                        actionTime = -1;
                        targetUUID = null;
                    }
                }

                case IDLE -> {
                    if (!modEnabled) return;

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

                    Vec3d eyePos = player.getEyePos();
                    Vec3d lookVec = player.getRotationVec(1.0f);

                    List<LivingEntity> nearby = client.world.getEntitiesByClass(
                        LivingEntity.class,
                        player.getBoundingBox().expand(3.5),
                        e -> e != player && e.isAlive()
                    );

                    for (LivingEntity enemy : nearby) {
                        UUID id = enemy.getUuid();

                        Vec3d toEnemy = enemy.getEyePos().subtract(eyePos).normalize();
                        double dot = lookVec.dotProduct(toEnemy);
                        boolean isLookingAt = dot > 0.97;

                        boolean isBlocking =
                            (enemy.getStackInHand(Hand.MAIN_HAND).isOf(Items.SHIELD) ||
                             enemy.getStackInHand(Hand.OFF_HAND).isOf(Items.SHIELD))
                            && enemy.isBlocking();

                        if (isBlocking && isLookingAt) {
                            Long lastHit = hitCooldowns.get(id);
                            boolean cooledDown = (lastHit == null || now - lastHit >= HIT_COOLDOWN_MS);

                            if (cooledDown) {
                                // Pre-emptively set cooldown to block double-triggers
                                hitCooldowns.put(id, now);

                                axeSlotPending = axeSlot;
                                originalSlotPending = currentSlot;
                                targetUUID = id;

                                state = SwapState.WAITING_TO_HIT;
                                actionTime = now + randomDelay();
                                break; // only handle one target per tick
                            }
                        }
                    }
                }
            }
        });
    }

    private LivingEntity getClosestLookedAtEnemy(MinecraftClient client) {
        if (client.player == null || client.world == null) return null;

        Vec3d eyePos = client.player.getEyePos();
        Vec3d lookVec = client.player.getRotationVec(1.0f);

        return client.world.getEntitiesByClass(
            LivingEntity.class,
            client.player.getBoundingBox().expand(3.5),
            e -> e != client.player && e.isAlive()
        ).stream().filter(e -> {
            Vec3d toEnemy = e.getEyePos().subtract(eyePos).normalize();
            return lookVec.dotProduct(toEnemy) > 0.97;
        }).min((a, b) -> {
            double distA = a.squaredDistanceTo(client.player);
            double distB = b.squaredDistanceTo(client.player);
            return Double.compare(distA, distB);
        }).orElse(null);
    }
}
