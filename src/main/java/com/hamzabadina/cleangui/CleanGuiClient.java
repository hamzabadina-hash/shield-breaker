package com.hamzabadina.cleangui;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CleanGuiClient implements ClientModInitializer {

    private static KeyBinding toggleKey;
    private static KeyBinding menuKey;
    private static boolean modEnabled = false;
    private static boolean stuntSlamEnabled = false;
    private static boolean wasTogglePressed = false;
    private static boolean wasMenuPressed = false;

    private static final Map<UUID, Long> hitCooldowns = new HashMap<>();
    private static final long HIT_COOLDOWN_MS = 1000;
    private static final double REACH = 3.0;

    // Stunt slam state
    private enum StuntState { IDLE, WAITING_FOLLOWUP }
    private static StuntState stuntState = StuntState.IDLE;
    private static int stuntOriginalSlot = -1;
    private static LivingEntity stuntTarget = null;
    private static long stuntFollowupTime = -1;

    public static class ShieldBreakerScreen extends Screen {

        public ShieldBreakerScreen() {
            super(Text.literal("§bShield Breaker §7Settings"));
        }

        @Override
        protected void init() {
            int centerX = this.width / 2;
            int centerY = this.height / 2;

            this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Shield Breaker: " + (modEnabled ? "§aON" : "§cOFF")),
                button -> {
                    modEnabled = !modEnabled;
                    button.setMessage(Text.literal("Shield Breaker: " + (modEnabled ? "§aON" : "§cOFF")));
                }
            ).dimensions(centerX - 100, centerY - 40, 200, 20).build());

            this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Stunt Slam: " + (stuntSlamEnabled ? "§aON" : "§cOFF")),
                button -> {
                    stuntSlamEnabled = !stuntSlamEnabled;
                    button.setMessage(Text.literal("Stunt Slam: " + (stuntSlamEnabled ? "§aON" : "§cOFF")));
                }
            ).dimensions(centerX - 100, centerY - 10, 200, 20).build());

            this.addDrawableChild(ButtonWidget.builder(
                Text.literal("§fClose"),
                button -> this.close()
            ).dimensions(centerX - 50, centerY + 30, 100, 20).build());
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            context.fill(0, 0, this.width, this.height, 0xAA000000);

            int panelX = this.width / 2 - 120;
            int panelY = this.height / 2 - 70;
            context.fill(panelX, panelY, panelX + 240, panelY + 140, 0xFF1A1A2E);
            context.fill(panelX, panelY, panelX + 240, panelY + 2, 0xFF4FC3F7);
            context.fill(panelX, panelY + 138, panelX + 240, panelY + 140, 0xFF4FC3F7);

            context.drawCenteredTextWithShadow(
                this.textRenderer,
                Text.literal("§b§lShield Breaker §7v1.2.0"),
                this.width / 2,
                this.height / 2 - 60,
                0xFFFFFF
            );

            context.drawCenteredTextWithShadow(
                this.textRenderer,
                Text.literal("§7Stunt Slam: break shield §f+§7 follow-up hit"),
                this.width / 2,
                this.height / 2 + 18,
                0xAAAAAA
            );

            context.drawCenteredTextWithShadow(
                this.textRenderer,
                Text.literal("§8Credits: §6SG_Mafia_"),
                this.width / 2,
                this.height / 2 + 58,
                0xFFFFFF
            );

            super.render(context, mouseX, mouseY, delta);
        }

        @Override
        public boolean shouldPause() {
            return false;
        }
    }

    @Override
    public void onInitializeClient() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.cleangui.toggle",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            "category.cleangui"
        ));

        menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.cleangui.menu",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            "category.cleangui"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;
            if (client.getNetworkHandler() == null) return;

            // Toggle mod with G
            if (toggleKey.isPressed() && !wasTogglePressed) {
                modEnabled = !modEnabled;
                wasTogglePressed = true;
                client.player.sendMessage(
                    Text.literal("§7[§bShield Breaker§7] "
                        + (modEnabled ? "§aON" : "§cOFF")
                        + (modEnabled && stuntSlamEnabled ? " §8| §eStunt Slam active" : "")),
                    true
                );
            }
            if (!toggleKey.isPressed()) wasTogglePressed = false;

            // Open menu with H
            if (menuKey.isPressed() && !wasMenuPressed) {
                wasMenuPressed = true;
                client.setScreen(new ShieldBreakerScreen());
            }
            if (!menuKey.isPressed()) wasMenuPressed = false;

            long now = System.currentTimeMillis();
            hitCooldowns.entrySet().removeIf(e -> now - e.getValue() > HIT_COOLDOWN_MS * 2);

            ClientPlayerEntity player = client.player;

            // Handle stunt slam follow-up tick
            if (stuntState == StuntState.WAITING_FOLLOWUP && now >= stuntFollowupTime) {
                if (stuntTarget != null && stuntTarget.isAlive()) {
                    // Now on original weapon - land the follow-up hit
                    client.interactionManager.attackEntity(player, stuntTarget);
                    player.swingHand(Hand.MAIN_HAND);
                }
                stuntState = StuntState.IDLE;
                stuntTarget = null;
                stuntOriginalSlot = -1;
                stuntFollowupTime = -1;
                return;
            }

            if (!modEnabled) return;

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

            // Don't trigger again while stunt slam is mid-sequence
            if (stuntState == StuntState.WAITING_FOLLOWUP) return;

            Vec3d eyePos = player.getEyePos();
            Vec3d lookVec = player.getRotationVec(1.0f);

            List<LivingEntity> nearby = client.world.getEntitiesByClass(
                LivingEntity.class,
                player.getBoundingBox().expand(REACH),
                e -> e != player && e.isAlive()
            );

            for (LivingEntity enemy : nearby) {
                UUID id = enemy.getUuid();

                double actualDist = enemy.getEyePos().distanceTo(eyePos);
                if (actualDist > REACH + 0.5) continue;

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

                        if (stuntSlamEnabled) {
                            // === STUNT SLAM MODE ===

                            // Step 1: swap TO axe and send packet
                            player.getInventory().selectedSlot = axeSlot;
                            client.getNetworkHandler().sendPacket(
                                new UpdateSelectedSlotC2SPacket(axeSlot)
                            );

                            // Step 2: hit with axe - breaks shield
                            client.interactionManager.attackEntity(player, enemy);
                            player.swingHand(Hand.MAIN_HAND);

                            // Step 3: swap BACK to original weapon immediately
                            player.getInventory().selectedSlot = originalSlot;
                            client.getNetworkHandler().sendPacket(
                                new UpdateSelectedSlotC2SPacket(originalSlot)
                            );

                            // Step 4: schedule follow-up hit next tick (50ms)
                            // so server has time to process the slot swap first
                            stuntState = StuntState.WAITING_FOLLOWUP;
                            stuntTarget = enemy;
                            stuntOriginalSlot = originalSlot;
                            stuntFollowupTime = now + 55;

                        } else {
                            // === NORMAL MODE ===
                            player.getInventory().selectedSlot = axeSlot;
                            client.getNetworkHandler().sendPacket(
                                new UpdateSelectedSlotC2SPacket(axeSlot)
                            );
                            client.interactionManager.attackEntity(player, enemy);
                            player.swingHand(Hand.MAIN_HAND);

                            player.getInventory().selectedSlot = originalSlot;
                            client.getNetworkHandler().sendPacket(
                                new UpdateSelectedSlotC2SPacket(originalSlot)
                            );
                        }

                        break;
                    }
                }
            }
        });
    }
}
