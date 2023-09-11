package bestsss.zstd;
/*Written by S. Simeonoff and released to the public domain, as explained at http://creativecommons.org/publicdomain/zero/1.0/
 * 2023*/
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

import com.sun.jna.Structure;

import bestsss.zstd.LibZstd.size_t;

public class OutputBuffer extends Structure{
//typedef struct ZSTD_outBuffer_s {
//  void*  dst;         /**< start of output buffer */
//  size_t size;        /**< size of output buffer */
//  size_t pos;         /**< position where writing stopped. Will be updated. Necessarily 0 <= pos <= size */
    
    public ByteBuffer dst;
    public size_t size;
    public size_t pos;
     
    public OutputBuffer() {}
    public OutputBuffer(ByteBuffer dst, int size, int pos) {
        super();
        this.dst = dst;
        this.size = new size_t(size);
        this.pos = new size_t(pos);
    }
    
    private static final List<String> order = Arrays.asList("dst", "size", "pos");
    @Override protected List<String> getFieldOrder() {return order;}
    
    public static OutputBuffer wrap(ByteBuffer buf) {        
        OutputBuffer  o = new OutputBuffer(buf, buf.remaining(), buf.position());
        return o;
    }    
}