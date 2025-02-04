package su.plo.voice.client.connection;

import com.google.common.io.ByteStreams;
import io.netty.buffer.ByteBufUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import org.jetbrains.annotations.NotNull;
import su.plo.voice.BaseVoice;
import su.plo.voice.api.client.connection.ServerConnection;
import su.plo.voice.client.BaseVoiceClient;
import su.plo.voice.proto.packets.Packet;
import su.plo.voice.proto.packets.PacketHandler;
import su.plo.voice.proto.packets.tcp.PacketTcpCodec;

//#if FABRIC
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

//#if MC>=12005
//$$ import su.plo.voice.codec.PacketTcpPayload;
//#else
import net.fabricmc.fabric.api.networking.v1.PacketSender;
//#endif

//#else
//$$ import net.minecraftforge.network.NetworkDirection;
//$$ import net.minecraftforge.network.NetworkEvent;
//#endif

import java.io.IOException;
import java.util.Optional;

public final class ModClientChannelHandler
    //#if FABRIC
    //#if MC>=12005
    //$$ implements ClientPlayNetworking.PlayPayloadHandler<PacketTcpPayload>
    //#else
    implements ClientPlayNetworking.PlayChannelHandler
    //#endif
    //#endif
{

    private final BaseVoiceClient voiceClient;

    private ModServerConnection connection;

    public ModClientChannelHandler(@NotNull BaseVoiceClient voiceClient) {
        this.voiceClient = voiceClient;
    }

    public void close() {
        if (connection != null) {
            voiceClient.getEventBus().unregister(voiceClient, connection);
            this.connection = null;
        }
    }

    public Optional<ServerConnection> getConnection() {
        return Optional.ofNullable(connection);
    }

    //#if FABRIC
    //#if MC>=12005
    //$$ @Override
    //$$ public void receive(PacketTcpPayload payload, ClientPlayNetworking.Context context) {
    //$$     Connection connection = context.client().getConnection().getConnection();
    //$$     receive(connection, payload.getPacket());
    //$$ }
    //#else
    @Override
    public void receive(Minecraft client,
                        ClientPacketListener handler,
                        FriendlyByteBuf buf,
                        PacketSender responseSender) {
        receive(handler.getConnection(), buf);
    }
    //#endif

    //#else
    //$$ public void receive(@NotNull NetworkEvent event) {
    //$$     NetworkEvent.Context context = event.getSource().get();
    //$$     if (context.getDirection() != NetworkDirection.PLAY_TO_CLIENT || event.getPayload() == null) return;
    //$$     receive(context.getNetworkManager(), event.getPayload());
    //$$     context.setPacketHandled(true);
    //$$ }
    //#endif

    private void receive(Connection connection, Packet<PacketHandler> packet) {
        if (this.connection == null || connection != this.connection.getConnection()) {
            if (this.connection != null) close();
            try {
                this.connection = new ModServerConnection(voiceClient, connection);
                this.connection.generateKeyPair();
                voiceClient.getEventBus().register(voiceClient, this.connection);
            } catch (Exception e) {
                BaseVoice.LOGGER.error("Failed to initialize server connection: {}", e.toString());
                e.printStackTrace();
                return;
            }
        }

        this.connection.handle(packet);
    }

    private void receive(Connection connection, FriendlyByteBuf buf) {
        byte[] data = ByteBufUtil.getBytes(buf.duplicate());

        try {
            PacketTcpCodec.decode(ByteStreams.newDataInput(data))
                    .ifPresent(packet -> receive(connection, packet));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
