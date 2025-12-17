package net.md_5.bungee.protocol.packet;

import io.netty.buffer.ByteBuf;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
@EqualsAndHashCode(callSuper = false)
public class Packet1Login extends DefinedPacket
{

    protected int protocolVersion;
    protected String username;
    protected long seed;
    protected int dimension;

    protected Packet1Login()
    {
        super( 0x01 );
    }

    public Packet1Login(int protocolVersion, String username, long seed, byte dimension)
    {
        this(protocolVersion, username, seed, (int) dimension );
    }

    public Packet1Login(int protocolVersion, String username, long seed, int dimension)
    {
        this();
        this.protocolVersion = protocolVersion;
        this.username = username;
        this.seed = seed;
        this.dimension = dimension;
    }

    @Override
    public void read(ByteBuf buf)
    {
        protocolVersion = buf.readInt();
        username = readString( buf );
        seed = buf.readLong();
        dimension = buf.readByte();
    }

    @Override
    public void write(ByteBuf buf)
    {
        buf.writeInt(protocolVersion);
        writeString(username, buf );
        buf.writeLong( seed );
        buf.writeByte( dimension );
    }

    @Override
    public void handle(AbstractPacketHandler handler) throws Exception
    {
        handler.handle( this );
    }
}
