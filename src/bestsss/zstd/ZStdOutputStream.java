package bestsss.zstd;
/*Written by S. Simeonoff and released to the public domain, as explained at http://creativecommons.org/publicdomain/zero/1.0/
 * 2023*/
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayDeque;

public class ZStdOutputStream extends OutputStream implements WritableByteChannel{
    final Sink out;

    final Pool pooled = Pool.pool();
    
    final InBuffer inBuffer=pooled.in;
    final OutputBuffer outBuffer=pooled.out;
    
    final ZstdRef zstd;
    

    public ZStdOutputStream(OutputStream out, int level) throws IOException {
        this(Sink.create(out), level);
    }
    
    public ZStdOutputStream(Sink sink, int level) throws IOException {
       this.out = sink;
        
       zstd = new ZstdRef();
       checkErr(LibZstd.impl.ZSTD_initCStream(zstd.address, level < 0 ? 5 : level));
       inBuffer.size.setValue(0);
    }
    
    @Override
    public void write(int b) throws IOException {
        byte[] x = {(byte)b};
        write(x);
    }
    
    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        ByteBuffer x = ByteBuffer.wrap(b, off, len);
        write(x);
    }
    
    public int write(ByteBuffer x) throws IOException{//future work - check x.isDirect and large enough - replace the buffer
        int result = x.remaining();
        for (ByteBuffer src = inBuffer.src; x.hasRemaining(); ) {
            int limit = x.limit();
            x.limit(Math.min(x.limit(), src.remaining()));
            
            src.put(x);
            x.limit(limit);
            
            if (!src.hasRemaining()) {//write when full
                writeInputBuffer(src); 
            }
        }
        return result;
    }

    private int writeInputBuffer(ByteBuffer src) throws IOException {
        src.flip();
        
        inBuffer.pos.setValue(0);
        inBuffer.size.setValue(src.limit()); /*inBuffer.src = src; if modified need to be restored*/
        int last=0;
        for (;inBuffer.pos.intValue() < inBuffer.size.intValue();) {
            last = writeImpl(LibZstd.FLUSH_CONTINUE);
        }
        src.clear();
        return last;
    }
    
    int writeImpl(int flushOp) throws IOException {
        synchronized(zstd) {
            long zstd = this.zstd.address();
            if (zstd == 0) {
                throw new IOException("closed already");   
            }
            if (flushOp!=LibZstd.FLUSH_CONTINUE  || outBuffer.size.intValue() - outBuffer.pos.intValue() < 1024) {
                sink();
            }
            int remainingInternal = checkErr(LibZstd.impl.ZSTD_compressStream2(zstd, outBuffer, inBuffer, flushOp));
            if (flushOp!=LibZstd.FLUSH_CONTINUE) {
                sink();
            }
            return remainingInternal;
        }
    }
    
    @Override
    public void flush() throws IOException {
        flushImpl(LibZstd.FLUSH_FLUSH);
        out.flush();
    }

    private void sink() throws IOException {
        outBuffer.dst.limit(outBuffer.pos.intValue());
        if (outBuffer.dst.hasRemaining())
            out.write(outBuffer.dst);
        outBuffer.dst.clear();
        outBuffer.pos.setValue(0);
    }

    static int checkErr(long res) throws IOException {
        if (res>=0)
            return (int) res;
        
        throw new IOException(String.format("Err: %d -- %s", res, LibZstd.impl.ZSTD_getErrorString(res)));
    }
    
    public void close() throws IOException {
        synchronized(zstd) {
            long zstd = this.zstd.address();
            if (zstd == 0) {
                return;
            }
            
            try {
                flushImpl(LibZstd.FLUSH_END);
                out.close();
            }finally {
               this.zstd.clear();   
               this.pooled.release();
            }
        }
    }
    
    private void flushImpl(int flushEnd) throws IOException {
        synchronized(zstd) {
            for(ByteBuffer src = inBuffer.src;;) {
                int last = 0;
                if (src.position() > 0) {
                    last = writeInputBuffer(src);
                }
                if (last!=0 || 
                    flushEnd == LibZstd.FLUSH_END) {
                    writeImpl(flushEnd);    
                }
                if (inBuffer.pos.intValue() == inBuffer.size.intValue())
                    break;
            }
        }
    }
    
    
    @Override
    public boolean isOpen() {
        return zstd.address != 0;
    }

    private static class ZstdRef {
        volatile long address;
        ZstdRef () {
            this.address = LibZstd.impl.ZSTD_createCStream();
        }

        long address() {return address;}
        void clear() {
            long zstd = address;
            address = 0;
            if (zstd!=0) {
                LibZstd.impl.ZSTD_freeCStream(zstd);
            }

        }
    }
    static class Pool{
        final InBuffer in;
        final OutputBuffer out;
        Pool(){// prefer large buffers to minimize the cost of jna trips
            in=new InBuffer(ByteBuffer.allocateDirect(1<<20).order(ByteOrder.nativeOrder()), 1<<20, 0);
            out=new OutputBuffer(ByteBuffer.allocateDirect(1<<20).order(ByteOrder.nativeOrder()), 1<<20, 0);
        }
        private static final int MAX_ELEMENTS = Math.max(2, Runtime.getRuntime().availableProcessors()/2);//some scaling based on the available cores
        static final ArrayDeque<Pool> entries = new ArrayDeque<>(MAX_ELEMENTS);
        public static Pool pool() {
            Pool p;
            synchronized (entries) {
                p = entries.pollLast();                
            }            
            return p != null ? p : new Pool();
        }
        
        void release() {
            in.size.setValue(in.src.capacity());
            in.pos.setValue(0);
            in.src.clear();
            
            out.size.setValue(out.dst.capacity());
            out.pos.setValue(0);
            out.dst.clear();
            
            synchronized (entries) {
                if (entries.size()<MAX_ELEMENTS)
                    entries.offer(this);
            }            
        }    
    }  

    static {
        JNAChecker.jna();
    }
}