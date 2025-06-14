package eu.doytchinov.tracecraft.net.packet;

import com.google.gson.JsonObject;
import eu.doytchinov.tracecraft.TraceCraft;
import eu.doytchinov.tracecraft.events.Event;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public record ClientMetricsPacket(int fps, long mem, long ping) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(fps);
        buf.writeVarLong(mem);
        buf.writeVarLong(ping);
    }

    public static ClientMetricsPacket decode(FriendlyByteBuf buf) {
        return new ClientMetricsPacket(buf.readVarInt(), buf.readVarLong(), buf.readVarLong());
    }

    public void handle(ServerPlayer sender) {
        JsonObject o = new JsonObject();
        o.addProperty("player", sender.getUUID().toString());
        o.addProperty("fps", fps);
        o.addProperty("mem", mem);
        o.addProperty("ping", ping);
        TraceCraft.QUEUE.addEvent(new Event(o, "client_metrics"));
    }
}
