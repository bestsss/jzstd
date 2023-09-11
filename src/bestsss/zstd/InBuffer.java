package bestsss.zstd;
/*Written by S. Simeonoff and released to the public domain, as explained at http://creativecommons.org/publicdomain/zero/1.0/
 * 2023*/
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

import com.sun.jna.*;

import bestsss.zstd.LibZstd.size_t;


public class InBuffer extends Structure{
    public ByteBuffer src;
    public size_t size;    
    public size_t pos;    
    
    public InBuffer() {}
    public InBuffer(ByteBuffer src, int size, int pos) {
        super();
        this.src =  src;
        this.size = new size_t(size);
        this.pos = new size_t(pos);
    }
    
    private static final List<String> order = Arrays.asList("src", "size", "pos");
    @Override
    protected List<String> getFieldOrder() {    
        return order;
    }
    public static InBuffer wrap(ByteBuffer src) {                
        return new InBuffer(src, src.remaining(), src.position());
    }
}
