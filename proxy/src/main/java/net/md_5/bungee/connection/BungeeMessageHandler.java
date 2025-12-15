package net.md_5.bungee.connection;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import lombok.RequiredArgsConstructor;
import net.md_5.bungee.BungeeCord;
import net.md_5.bungee.ServerConnection;
import net.md_5.bungee.UserConnection;
import net.md_5.bungee.Util;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.protocol.packet.PacketF9BungeeMessage;

import java.io.DataInput;
import java.io.IOException;

@RequiredArgsConstructor
public class BungeeMessageHandler {
    private final ServerConnection serverConnection;
    private final UserConnection userConnection;

    private final BungeeCord bungee = BungeeCord.getInstance();

    public void handleBungeeMessage(PacketF9BungeeMessage packet) throws IOException {
        DataInput in = packet.getStream();
        PluginMessageEvent event = new PluginMessageEvent( serverConnection, userConnection, packet.getData().clone() );

        if ( bungee.getPluginManager().callEvent( event ).isCancelled() )
        {
            throw new CancelSendSignal();
        }

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        String subChannel = in.readUTF();

        if ( subChannel.equals( "Forward" ) )
        {
            // Read data from server
            String target = in.readUTF();
            String channel = in.readUTF();
            short len = in.readShort();
            byte[] data = new byte[ len ];
            in.readFully( data );

            // Prepare new data to send
            out.writeUTF( channel );
            out.writeShort( data.length );
            out.write( data );
            byte[] payload = out.toByteArray();

            // Null out stream, important as we don't want to send to ourselves
            out = null;

            if ( target.equals( "ALL" ) )
            {
                for ( ServerInfo server : bungee.getServers().values() )
                {
                    if ( server != serverConnection.getInfo() )
                    {
                        server.sendData( payload );
                    }
                }
            } else
            {
                ServerInfo server = bungee.getServerInfo( target );
                if ( server != null )
                {
                    server.sendData( payload );
                }
            }
        }
        if ( subChannel.equals( "Connect" ) )
        {
            ServerInfo server = bungee.getServerInfo( in.readUTF() );
            if ( server != null )
            {
                userConnection.connect( server );
            }
        }
        if ( subChannel.equals( "ConnectOther" ) )
        {
            ProxiedPlayer player = bungee.getPlayer( in.readUTF() );
            if ( player != null )
            {
                ServerInfo server = bungee.getServerInfo( in.readUTF() );
                if ( server != null )
                {
                    player.connect( server );
                }
            }
        }
        if ( subChannel.equals( "IP" ) )
        {
            out.writeUTF( "IP" );
            out.writeUTF( userConnection.getAddress().getHostString() );
            out.writeInt( userConnection.getAddress().getPort() );
        }
        if ( subChannel.equals( "PlayerCount" ) )
        {
            String target = in.readUTF();
            out.writeUTF( "PlayerCount" );
            if ( target.equals( "ALL" ) )
            {
                out.writeUTF( "ALL" );
                out.writeInt( bungee.getOnlineCount() );
            } else
            {
                ServerInfo server = bungee.getServerInfo( target );
                if ( server != null )
                {
                    out.writeUTF( server.getName() );
                    out.writeInt( server.getPlayers().size() );
                }
            }
        }
        if ( subChannel.equals( "PlayerList" ) )
        {
            String target = in.readUTF();
            out.writeUTF( "PlayerList" );
            if ( target.equals( "ALL" ) )
            {
                out.writeUTF( "ALL" );
                out.writeUTF( Util.csv( bungee.getPlayers() ) );
            } else
            {
                ServerInfo server = bungee.getServerInfo( target );
                if ( server != null )
                {
                    out.writeUTF( server.getName() );
                    out.writeUTF( Util.csv( server.getPlayers() ) );
                }
            }
        }
        if ( subChannel.equals( "GetServers" ) )
        {
            out.writeUTF( "GetServers" );
            out.writeUTF( Util.csv( bungee.getServers().keySet() ) );
        }
        if ( subChannel.equals( "Message" ) )
        {
            ProxiedPlayer target = bungee.getPlayer( in.readUTF() );
            if ( target != null )
            {
                target.sendMessage( in.readUTF() );
            }
        }
        if ( subChannel.equals( "GetServer" ) )
        {
            out.writeUTF( "GetServer" );
            out.writeUTF( serverConnection.getInfo().getName() );
        }

        // Check we haven't set out to null, and we have written data, if so reply back back along the BungeeCord channel
        if ( out != null )
        {
            byte[] b = out.toByteArray();
            if ( b.length != 0 )
            {
                serverConnection.sendData( b );
            }
        }
    }
}
