package net.md_5.bungee.protocol;

import io.netty.buffer.ByteBuf;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import lombok.Getter;
import static net.md_5.bungee.protocol.OpCode.*;
import net.md_5.bungee.protocol.packet.DefinedPacket;
import net.md_5.bungee.protocol.packet.Packet0KeepAlive;
import net.md_5.bungee.protocol.packet.Packet1Login;
import net.md_5.bungee.protocol.packet.Packet2Handshake;
import net.md_5.bungee.protocol.packet.Packet3Chat;
import net.md_5.bungee.protocol.packet.Packet9Respawn;
import net.md_5.bungee.protocol.packet.PacketF9BungeeMessage;
import net.md_5.bungee.protocol.packet.PacketFEPing;
import net.md_5.bungee.protocol.packet.PacketFFKick;
import net.md_5.bungee.protocol.skip.PacketReader;

public class Vanilla implements Protocol
{

    public static final byte PROTOCOL_VERSION = 14;
    public static final String GAME_VERSION = "Beta 1.7.3";
    @Getter
    private static final Vanilla instance = new Vanilla();
    /*========================================================================*/
    @Getter
    private final OpCode[][] opCodes = new OpCode[ 256 ][];
    @SuppressWarnings("unchecked")
    @Getter
    protected Class<? extends DefinedPacket>[] classes = new Class[ 256 ];
    @SuppressWarnings("unchecked")
    @Getter
    private Constructor<? extends DefinedPacket>[] constructors = new Constructor[ 256 ];
    @Getter
    protected PacketReader skipper;
    /*========================================================================*/

    public Vanilla()
    {
        classes[0x00] = Packet0KeepAlive.class;
        classes[0x01] = Packet1Login.class;
        classes[0x02] = Packet2Handshake.class;
        classes[0x03] = Packet3Chat.class;
        classes[0x09] = Packet9Respawn.class;
        classes[0xF9] = PacketF9BungeeMessage.class;
        classes[0xFE] = PacketFEPing.class;
        classes[0xFF] = PacketFFKick.class;
        skipper = new PacketReader( this );
    }

    @Override
    public DefinedPacket read(short packetId, ByteBuf buf)
    {
        int start = buf.readerIndex();
        DefinedPacket packet = read( packetId, buf, this );
        // For keepalive, there's no data to read
        if ( packetId != 0x00 && buf.readerIndex() == start )
        {
            throw new BadPacketException( "Unknown packet id " + packetId );
        }
        return packet;
    }

    public static DefinedPacket read(short id, ByteBuf buf, Protocol protocol)
    {
        DefinedPacket packet = packet( id, protocol );
        if ( packet != null )
        {
            packet.read( buf );
            return packet;
        }
        protocol.getSkipper().tryRead( id, buf );
        return null;
    }

    public static DefinedPacket packet(short id, Protocol protocol)
    {
        DefinedPacket ret = null;
        Class<? extends DefinedPacket> clazz = protocol.getClasses()[id];

        if ( clazz != null )
        {
            try
            {
                Constructor<? extends DefinedPacket> constructor = protocol.getConstructors()[id];
                if ( constructor == null )
                {
                    constructor = clazz.getDeclaredConstructor();
                    constructor.setAccessible( true );
                    protocol.getConstructors()[id] = constructor;
                }

                if ( constructor != null )
                {
                    ret = constructor.newInstance();
                }
            } catch ( NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex )
            {
            }
        }

        return ret;
    }

    
    {
        opCodes[0x04] = new OpCode[]
        {
            LONG
        };
        opCodes[0x05] = new OpCode[]
        {
            INT, SHORT, SHORT, SHORT
        };
        opCodes[0x06] = new OpCode[]
        {
            INT, INT, INT
        };
        opCodes[0x07] = new OpCode[]
        {
            INT, INT, BOOLEAN
        };
        opCodes[0x08] = new OpCode[]
        {
            SHORT
        };
        opCodes[0x0A] = new OpCode[]
        {
            BOOLEAN
        };
        opCodes[0x0B] = new OpCode[]
        {
            DOUBLE, DOUBLE, DOUBLE, DOUBLE, BOOLEAN
        };
        opCodes[0x0C] = new OpCode[]
        {
            FLOAT, FLOAT, BOOLEAN
        };
        opCodes[0x0D] = new OpCode[]
        {
            DOUBLE, DOUBLE, DOUBLE, DOUBLE, FLOAT, FLOAT, BOOLEAN
        };
        opCodes[0x0E] = new OpCode[]
        {
            BYTE, INT, BYTE, INT, BYTE
        };
        opCodes[0x0F] = new OpCode[]
        {
            INT, BYTE, INT, BYTE, ITEM
        };
        opCodes[0x10] = new OpCode[]
        {
            SHORT
        };
        opCodes[0x11] = new OpCode[]
        {
            INT, BYTE, INT, BYTE, INT
        };
        opCodes[0x12] = new OpCode[]
        {
            INT, BYTE
        };
        opCodes[0x13] = new OpCode[]
        {
            INT, BYTE
        };
        opCodes[0x14] = new OpCode[]
        {
            INT, STRING, INT, INT, INT, BYTE, BYTE, SHORT
        };
		opCodes[0x15] = new OpCode[]{
            INT, SHORT, BYTE, SHORT, INT, INT, INT, BYTE, BYTE, BYTE
        };
        opCodes[0x16] = new OpCode[]
        {
            INT, INT
        };
        opCodes[0x17] = new OpCode[]
        {
            INT, BYTE, INT, INT, INT, OPTIONAL_MOTION
        };
        opCodes[0x18] = new OpCode[]
        {
            INT, BYTE, INT, INT, INT, BYTE, BYTE, METADATA
        };
        opCodes[0x19] = new OpCode[]
        {
            INT, STRING, INT, INT, INT, INT
        };
        opCodes[0x1B] = new OpCode[]
        {
            FLOAT, FLOAT, FLOAT, FLOAT, BOOLEAN, BOOLEAN
        };
        opCodes[0x1C] = new OpCode[]
        {
            INT, SHORT, SHORT, SHORT
        };
        opCodes[0x1D] = new OpCode[]
        {
            INT
        };
        opCodes[0x1E] = new OpCode[]
        {
            INT
        };
        opCodes[0x1F] = new OpCode[]
        {
            INT, BYTE, BYTE, BYTE
        };
        opCodes[0x20] = new OpCode[]
        {
            INT, BYTE, BYTE
        };
        opCodes[0x21] = new OpCode[]
        {
            INT, BYTE, BYTE, BYTE, BYTE, BYTE
        };
        opCodes[0x22] = new OpCode[]
        {
            INT, INT, INT, INT, BYTE, BYTE
        };
        opCodes[0x26] = new OpCode[]
        {
            INT, BYTE
        };
        opCodes[0x27] = new OpCode[]
        {
            INT, INT
        };
        opCodes[0x28] = new OpCode[]
        {
            INT, METADATA
        };
		opCodes[0x32] = new OpCode[]{
            INT, INT, BYTE
        };
        opCodes[0x33] = new OpCode[]
        {
            INT, SHORT, INT, BYTE, BYTE, BYTE, INT_BYTE
        };
        opCodes[0x34] = new OpCode[]
        {
            INT, INT, BLOCK_CHANGE_ARRAY
        };
        opCodes[0x35] = new OpCode[]
        {
            INT, BYTE, INT, BYTE, BYTE
        };
        opCodes[0x36] = new OpCode[]
        {
            INT, SHORT, INT, BYTE, BYTE
        };
        opCodes[0x3C] = new OpCode[]
        {
            DOUBLE, DOUBLE, DOUBLE, FLOAT, INT_3
        };
        opCodes[0x3D] = new OpCode[]
        {
            INT, INT, BYTE, INT, INT
        };
        opCodes[0x46] = new OpCode[]
        {
            BYTE
        };
        opCodes[0x47] = new OpCode[]
        {
            INT, BYTE, INT, INT, INT
        };
        opCodes[0x64] = new OpCode[]
        {
            BYTE, BYTE, SHORT_BYTE, BYTE
        };
        opCodes[0x65] = new OpCode[]
        {
            BYTE
        };
        opCodes[0x66] = new OpCode[]
        {
            BYTE, SHORT, BYTE, SHORT, BOOLEAN, ITEM
        };
        opCodes[0x67] = new OpCode[]
        {
            BYTE, SHORT, ITEM
        };
        opCodes[0x68] = new OpCode[]
        {
            BYTE, SHORT_ITEM
        };
        opCodes[0x69] = new OpCode[]
        {
            BYTE, SHORT, SHORT
        };
        opCodes[0x6A] = new OpCode[]
        {
            BYTE, SHORT, BOOLEAN
        };
        opCodes[0x82] = new OpCode[]
        {
            INT, SHORT, INT, STRING, STRING, STRING, STRING
        };
        opCodes[0x83] = new OpCode[]
        {
            SHORT, SHORT, BYTE_BYTE
        };
        opCodes[0xC8] = new OpCode[]
        {
            INT, BYTE
        };
    }
}
