package bestsss.zstd;
/*Written by S. Simeonoff and released to the public domain, as explained at http://creativecommons.org/publicdomain/zero/1.0/
 * 2023*/
import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.*;

public abstract class Sink implements WritableByteChannel, Flushable{ // the class is left extendable to allow different impl. as well
    
    public static Sink create(Socket socket) throws IOException {
        SocketChannel ch = socket.getChannel();
        if (ch != null && ch.isBlocking()) {
            return new ChannelSink(ch, ch);
        }
        return create(socket.getOutputStream());
    }
    
    public static Sink create(FileOutputStream out) {
        return create(out);
    }
    
    public static Sink create(OutputStream out) {              
        ByteChannel channel;
        if (out instanceof FileOutputStream && (channel =((FileOutputStream) out).getChannel() ) != null) {
            return new ChannelSink(channel, out);
        }

        return new OutpuStreamSink(out);
    }
    
    public static Sink create(WritableByteChannel channel) {
        return new ChannelSink(channel, channel);
    }

    ///////////
    private static class ChannelSink extends Sink{
        final WritableByteChannel channel;
        final Closeable closable;

        public ChannelSink(WritableByteChannel channel, Closeable closable) {
            this.channel = channel;
            this.closable = closable;
        }

        @Override public void close() throws IOException {closable.close();}

        @Override public int write(ByteBuffer src) throws IOException {return channel.write(src);}
        @Override public boolean isOpen() {return channel.isOpen();}

        @Override
        public void flush() throws IOException {
            if (closable instanceof Flushable) {
                ((Flushable) closable).flush();
            }
        }
    }

    private static class OutpuStreamSink extends Sink {
        final OutputStream out;
        byte[] sink;//delay creation based on the amount of data written
        boolean open = true;

        public OutpuStreamSink(OutputStream out) {
            this.out = out;
        }

        @Override
        public int write(ByteBuffer src) throws IOException {            
            int n = src.remaining();
            if (sink == null)
                sink = new byte[n > 1<<16 ? 1<<17 : 1<<13];
            
            for (; src.hasRemaining();) {
                int len = Math.min(src.remaining(), sink.length);
                src.get(sink, 0, len);
                out.write(sink, 0, len);
            }
            return n;
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public void close() throws IOException {
            open = false;
            out.close();
        }

        @Override public void flush() throws IOException {out.flush();}        
    }
}