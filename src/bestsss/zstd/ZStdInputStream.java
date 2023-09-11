package bestsss.zstd;
/*Written by S. Simeonoff and released to the public domain, as explained at http://creativecommons.org/publicdomain/zero/1.0/
 * 2023*/
import bestsss.zstd.ZStdOutputStream.Pool;
import static bestsss.zstd.ZStdOutputStream.checkErr;

import java.io.*;
import java.nio.channels.FileChannel;

public class ZStdInputStream extends InputStream{
    private final InputStream in;
    private final Pool pooled = Pool.pool();
    
    private final InBuffer inBuffer=pooled.in;
    private final OutputBuffer outBuffer=pooled.out;
    private final ZstdRef zstd;
    
    private static class ZstdRef {
        volatile long address;
        ZstdRef () {
            this.address = LibZstd.impl.ZSTD_createDStream();
        }

        long address() {return address;}
        void clear() {
            long zstd = address;
            address = 0;
            if (zstd!=0) {
                LibZstd.impl.ZSTD_freeDStream(zstd);
            }
        }
    }
    
    public ZStdInputStream(InputStream in) throws IOException {
        this.in = in;
        zstd = new ZstdRef();
        checkErr(LibZstd.impl.ZSTD_initDStream(zstd.address));
        inBuffer.size.setValue(0);//empty
        outBuffer.size.setValue(0);outBuffer.dst.limit(0); //empty
    }
    
    @Override
    public int read() throws IOException {
        byte[] b= {0};
        int n = read(b);
        return n<0 ? n : b[0];
    }

    private boolean eof;
    public int read(byte[] b, int off, int len) throws IOException {
        if (off < 0 || len < 0 || len > b.length - off) 
            throw new IndexOutOfBoundsException();        
        if (zstd.address() == 0) 
            throw new IOException("closed already");
        
        if (!outBuffer.dst.hasRemaining()) {            
            fillIn();
            if (!outBuffer.dst.hasRemaining())
                return eof ? -1 : 0;
        }
        
        int n = Math.min(outBuffer.dst.remaining(), len);
        outBuffer.dst.get(b, off, n);
        return n;          
    }
    
    private byte[] srcBuf;
    private void fillIn() throws IOException {        
        synchronized(zstd) {
            final long zstd = this.zstd.address();
            if (zstd == 0) {
                throw new IOException("closed already");   
            }   
            outBuffer.pos.setValue(0);outBuffer.size.setValue(outBuffer.dst.clear().capacity());//empty it
            
            if (inBuffer.pos.intValue() >= inBuffer.size.intValue()) {
                inBuffer.src.clear();
                
                int n = 0;
                
                FileChannel channel ;                
                if (in instanceof FileInputStream && null!=(channel = ((FileInputStream)in).getChannel())) {
                    
                    n = channel.read(inBuffer.src);                           
                } else {
                    if (srcBuf == null) {
                        srcBuf =new byte[in.available() < 8192 ? 8192 : 32768];                        
                    }
                    for (int len;(len = Math.min(srcBuf.length, inBuffer.src.remaining())) > 0 &&  (n=in.read(srcBuf, 0, len))>0;) {
                        inBuffer.src.put(srcBuf, 0, n);        
                    }
                }
                inBuffer.src.flip();
                inBuffer.size.setValue(inBuffer.src.remaining()); inBuffer.pos.setValue(0);
                eof |= n<0;
            }
            
            checkErr(LibZstd.impl.ZSTD_decompressStream(zstd, outBuffer, inBuffer));
            outBuffer.dst.limit(outBuffer.pos.intValue());
             
            
        }
    }

    public void close() throws IOException {
        synchronized(zstd) {
            long zstd = this.zstd.address();
            if (zstd == 0) {
                return;
            }
            
            try {
                in.close();
            }finally {
               this.zstd.clear();   
               this.pooled.release();
            }
        }
    }
    static {
        JNAChecker.jna();
    }
}