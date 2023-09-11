package bestsss.zstd;
/*Written by S. Simeonoff and released to the public domain, as explained at http://creativecommons.org/publicdomain/zero/1.0/
 * 2023*/
import java.io.File;

import com.sun.jna.*;

class JNAChecker {
    static {
        try{
            @SuppressWarnings("unused")
            int k = Native.SIZE_T_SIZE; k++;
            try {
                Package p = Native.class.getPackage();
                String ver = p!=null ? p.getImplementationVersion() : null;
                if (ver!=null && Integer.parseInt(ver.substring(0, ver.indexOf('.'))) < 5) {//major version less than 5
                    throw new LinkageError(String.format("JNA version[%s] is lower than 5. Update dependencies to include a higher version of jna. e.g. %s", ver, depMvn()));
                }
            }catch(RuntimeException _ignore) {}
        }catch(NoClassDefFoundError _noNative) {
            throw (LinkageError) new LinkageError(
                    "This is how it is: com.sun.jna.Native (jna library) must be available for jzstd to work. Maven dependency:"+
                    depMvn()+
        " Error: "+_noNative.getMessage()).initCause(_noNative);
        }
        if (Platform.isMac()) {
            NativeLibrary.addSearchPath("zstd", "/usr/local/lib/");
            java.io.File f= new File("/opt/homebrew/Cellar/zstd/");//homebrew odd installation path
            if (f.exists() && null != (f = findDylib(f))){
                NativeLibrary.addSearchPath("zstd", f.getAbsolutePath());   
            }
        }
        initLibZstd();
    }
    private static void initLibZstd() {        
        try {
            LibZstd.impl.ZSTD_isError(-1L);
        }catch (UnsatisfiedLinkError _cantFindLib) {
            String msg = "Can't init libzstd. This is how it is: Could not find zstd on the search path. If running on Mac, try installting it via 'brew install zstd'.\n"+
            "If the library exists on the machine setting it via system property -Djna.platform.library.path=<dir> would work as well.\n"+
            "Setting system property to enable more logging -Djna.debug_load=true\n"+
            "The nested exception contains the attempted search paths to resolve the library"
            ;            
            throw (UnsatisfiedLinkError) new UnsatisfiedLinkError(msg).initCause(_cantFindLib);            
        }catch (LinkageError _otherLinkage) {
            String msg = "Can't init libzstd. This is how it is: Likely JNA version is incomptible - requires 5.x.x, or the local system has too low version of libzstd (req. 1.4.4).\n"+
                         "Enable debug logging via setting system property logging -Djna.debug_load=true\n"+
                         "Check zstd version via 'zstd --version'";
            throw (UnsatisfiedLinkError) new UnsatisfiedLinkError(msg).initCause(_otherLinkage);                        
        }
    }
    
    private static String depMvn() {
        return
        "\n"+        
        "<dependency>\n"+
        "    <groupId>net.java.dev.jna</groupId>\n"+
        "    <artifactId>jna</artifactId>\n"+
        "    <version>5.13.0</version>\n"+
        "</dependency>\n";
    }

    private static File findDylib(File dir) {        
        for (File f : dir.listFiles()) {
            if (f.isFile() && f.getName().endsWith(".dylib"))
                return dir;
            if (f.isDirectory() && null!=(f = findDylib(f))) {
                return f;                                                   
            }                
        }
        return null;        
    }
    
    static void jna() {}
}