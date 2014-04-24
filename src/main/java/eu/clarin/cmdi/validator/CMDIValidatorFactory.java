package eu.clarin.cmdi.validator;

import java.io.File;

import net.sf.saxon.s9api.Processor;

import org.apache.commons.lang3.SystemUtils;

public class CMDIValidatorFactory {
    private final Processor processor = new Processor(true);
    private final SchemaLoader schemaLoader;


    private CMDIValidatorFactory(File cacheDirectory)
            throws CMDIValidatorInitException {
        if (cacheDirectory == null) {
            if (SystemUtils.IS_OS_WINDOWS &&
                    (SystemUtils.JAVA_IO_TMPDIR != null)) {
                cacheDirectory = new File(SystemUtils.JAVA_IO_TMPDIR,
                        "cmdi-validator");
            } else if (SystemUtils.IS_OS_UNIX &&
                    (SystemUtils.USER_HOME != null)) {
                cacheDirectory = new File(SystemUtils.USER_HOME,
                        ".cmdi-validator");
            }
            if (cacheDirectory != null) {
                if (!cacheDirectory.exists()) {
                    if (!cacheDirectory.mkdir()) {
                        throw new CMDIValidatorInitException(
                                "cannot create cache directory: " +
                                        cacheDirectory);
                    }
                }
            } else {
                if (SystemUtils.JAVA_IO_TMPDIR == null) {
                    throw new CMDIValidatorInitException(
                            "cannot determine temporary directory");
                }
                cacheDirectory = new File(SystemUtils.JAVA_IO_TMPDIR);
            }
        } else {
            if (!cacheDirectory.isDirectory()) {
                throw new CMDIValidatorInitException(
                        "supplied cache dircetory '" +
                                cacheDirectory.getAbsolutePath() +
                                "' is not a directory");
            }
            if (!cacheDirectory.canWrite()) {
                throw new CMDIValidatorInitException("cache dircetory '" +
                        cacheDirectory.getAbsolutePath() + "' is not writable");
            }
        }
        schemaLoader = new SchemaLoader(cacheDirectory,
                SchemaLoader.DISABLE_CACHE_AGING);
    }


    public CMDIValidator newValidator() throws CMDIValidatorInitException {
        return new CMDIValidator(processor, schemaLoader);
    }


    public static CMDIValidatorFactory newInstance(File cacheDircetory)
            throws CMDIValidatorInitException {
        return new CMDIValidatorFactory(cacheDircetory);
    }


    public static CMDIValidatorFactory newInstance()
            throws CMDIValidatorInitException {
        return new CMDIValidatorFactory(null);
    }

} // class CMDIValidatorFactory
