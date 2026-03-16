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

    // Cooldown per enemy so we don't spam every tick
    private static final Map<UUID, Long> hitCooldowns = new HashMap<>();
    private static final long HIT_COOLDOWN_MS = 1000;

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

            if (!modEnabled) return;

            long now = System.currentTimeMillis();

            // Clean stale cooldowns
            hitCooldowns.entrySet().removeIf(e -> now - e.getValue() > HIT_COOLDOWN_MS * 2);

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

            int originalSlot = player.getInventory().selectedSlot;
            if (originalSlot == axeSlot) return;

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
                        hitCooldowns.put(id, now);

                        // Swap to axe instantly
                        player.getInventory().selectedSlot = axeSlot;
                        client.getNetworkHandler().sendPacket(
                            new UpdateSelectedSlotC2SPacket(axeSlot)
                        );

                        // Attack instantly same tick
                        client.interactionManager.attackEntity(player, enemy);
                        player.swingHand(Hand.MAIN_HAND);

                        // Swap back instantly
                        player.getInventory().selectedSlot = originalSlot;
                        client.getNetworkHandler().sendPacket(
                            new UpdateSelectedSlotC2SPacket(originalSlot)
                        );

                        break;
                    }
                }
            }
        });
    }
}
