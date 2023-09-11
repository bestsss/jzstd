package bestsss.zstd;
/*Written by S. Simeonoff and released to the public domain, as explained at http://creativecommons.org/publicdomain/zero/1.0/
 * 2023
 * libzstd is licensed under 2-Clause BSD -
 * */
import com.sun.jna.*;

public interface LibZstd extends Library{
    static final LibZstd impl = Native.load(/*lib*/"zstd", LibZstd.class);
    
    boolean ZSTD_isError(long result); 
    String ZSTD_getErrorString(long err);
    
    long ZSTD_createCStream();
    long ZSTD_freeCStream(long zstd);
    
    long ZSTD_initCStream(long zstd, int level);
        
    long ZSTD_compressStream2(long zstd, OutputBuffer output, InBuffer input,
                                             /*ZSTD_EndDirective endOp*/  int flushOp);
    
    /** collect more data, encoder decides when to output compressed result, for optimal compression ratio */
    int FLUSH_CONTINUE =0;// ZSTD_e_continue=0
   
/** flush any data provided so far,
 * it creates (at least) one new block, that can be decoded immediately on reception;
 * frame will continue: any future data can still reference previously compressed data, improving compression.
 */
    int FLUSH_FLUSH = 1;//        ZSTD_e_flush=1
 
  /** flush any remaining data _and_ close current frame.
  * note that frame is only closed after compressed data is fully flushed (return value == 0).
  * After that point, any additional data starts a new frame.
  * note : each frame is independent (does not reference any content from previous frame).*/
    int FLUSH_END = 2; //ZSTD_e_end=2


    public static class size_t extends IntegerType {
        private static final long serialVersionUID = 0L;
        public size_t() {
            this(0);
        }
        public size_t(long value) {
            super(Native.SIZE_T_SIZE, true);
            setValue(value);
        }
    }
    
//decompress
    long ZSTD_createDStream();
    long ZSTD_initDStream(long zstd);
    long ZSTD_freeDStream(long zstd);
    long ZSTD_decompressStream(long zstd, OutputBuffer output, InBuffer input);
}