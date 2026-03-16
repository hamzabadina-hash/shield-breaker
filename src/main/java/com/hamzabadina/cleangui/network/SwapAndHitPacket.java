package com.hamzabadina.cleangui.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.util.Identifier;

public class SwapAndHitPacket {

    public static final Identifier ID = Identifier.of("cleangui", "swap_hit");

    public record Payload(int axeSlot, int originalSlot) implements CustomPayload {
        public static final CustomPayload.Id<Payload> PACKET_ID = new CustomPayload.Id<>(ID);
        public static final PacketCodec<PacketByteBuf, Payload> CODEC =
            PacketCodec.of(
                (value, buf) -> { buf.writeInt(value.axeSlot()); buf.writeInt(value.originalSlot()); },
                buf -> new Payload(buf.readInt(), buf.readInt())
            );

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return PACKET_ID;
        }
    }

    public static void registerReceiver() {
        PayloadTypeRegistry.playC2S().register(Payload.PACKET_ID, Payload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(Payload.PACKET_ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            int axeSlot = payload.axeSlot();
            int originalSlot = payload.originalSlot();

            context.server().execute(() -> {
                if (axeSlot < 0 || axeSlot > 8) return;
                if (!(player.getInventory().getStack(axeSlot).getItem() instanceof AxeItem)) return;

                player.getInventory().selectedSlot = axeSlot;

                player.getWorld().getEntitiesByClass(
                    net.minecraft.entity.LivingEntity.class,
                    player.getBoundingBox().expand(3.5),
                    e -> e != player && e.isAlive()
                ).stream().findFirst().ifPresent(target -> {
                    player.attack(target);
                });

                player.getServer().execute(() -> {
                    player.getInventory().selectedSlot = originalSlot;
                });
            });
        });
    }
}
