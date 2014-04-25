package eu.clarin.cmdi.validator;

import java.io.File;
import java.net.URL;

import javax.xml.transform.stream.StreamSource;

import net.sf.saxon.s9api.DocumentBuilder;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmDestination;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XsltCompiler;
import net.sf.saxon.s9api.XsltExecutable;
import net.sf.saxon.s9api.XsltTransformer;

import org.apache.commons.lang3.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class CMDIValidatorFactory {
    private static final String SCHEMATATRON_STAGE_1 =
            "/schematron/iso_dsdl_include.xsl";
    private static final String SCHEMATATRON_STAGE_2 =
            "/schematron/iso_abstract_expand.xsl";
    private static final String SCHEMATATRON_STAGE_3 =
            "/schematron/iso_svrl_for_xslt2.xsl";
    private static final String DEFAULT_SCHEMATRON_SCHEMA =
            "/default.sch";
    private static final Logger logger =
            LoggerFactory.getLogger(CMDIValidatorFactory.class);
    private final Processor processor;
    private final SchemaLoader schemaLoader;
    private final XsltExecutable schematronValidator;


    private CMDIValidatorFactory(File cacheDirectory, boolean disableSchematron)
            throws CMDIValidatorInitException {
        /*
         * initialize custom schema loader
         */
        logger.debug("initializing schema loader ...");
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

        /*
         * initialize Saxon processor
         */
        logger.debug("initializing Saxon ...");
        this.processor = new Processor(true);

        /*
         * initialize Schematron validator
         */
        if (!disableSchematron) {
            logger.debug("initializing Schematron validator ...");
            URL schema = this.getClass().getResource(DEFAULT_SCHEMATRON_SCHEMA);
            if (schema == null) {
                throw new CMDIValidatorInitException(
                        "cannot locate schematron schema");
            }
            final XsltCompiler compiler = processor.newXsltCompiler();
            XsltTransformer stage1 =
                    loadStylesheet(compiler, SCHEMATATRON_STAGE_1);
            XsltTransformer stage2 =
                    loadStylesheet(compiler, SCHEMATATRON_STAGE_2);
            XsltTransformer stage3 =
                    loadStylesheet(compiler, SCHEMATATRON_STAGE_3);
            try {
                XdmDestination destination = new XdmDestination();
                stage1.setSource(new StreamSource(schema.toExternalForm()));
                stage1.setDestination(stage2);
                stage2.setDestination(stage3);
                stage3.setDestination(destination);
                stage1.transform();
                schematronValidator =
                        compiler.compile(destination.getXdmNode().asSource());
                logger.debug("Schematron validator successfully initializied");
            } catch (SaxonApiException e) {
                throw new CMDIValidatorInitException(
                        "error compiling schematron rules", e);
            }
        } else {
            logger.debug("disabling Schematron validator");
            this.schematronValidator = null;
        }
    }


    public CMDIValidator newValidator() throws CMDIValidatorInitException {
        return new CMDIValidator(processor, schemaLoader, schematronValidator);
    }


    public static CMDIValidatorFactory newInstance(File cacheDircetory,
            boolean disableSchematron) throws CMDIValidatorInitException {
        return new CMDIValidatorFactory(cacheDircetory, disableSchematron);
    }


    public static CMDIValidatorFactory newInstance()
            throws CMDIValidatorInitException {
        return new CMDIValidatorFactory(null, false);
    }


    private XsltTransformer loadStylesheet(XsltCompiler compiler, String name)
            throws CMDIValidatorInitException {
        try {
            logger.debug("loading stylesheet '{}'", name);
            final URL uri = this.getClass().getResource(name);
            if (uri != null) {
                DocumentBuilder builder = processor.newDocumentBuilder();
                XdmNode source =
                        builder.build(new StreamSource(uri.toExternalForm()));
                XsltExecutable stylesheet = compiler.compile(source.asSource());
                return stylesheet.load();
            } else {
                throw new CMDIValidatorInitException("cannot find resource '" +
                        name + "'");
            }
        } catch (SaxonApiException e) {
            throw new CMDIValidatorInitException(
                    "error loading schematron stylesheet '" + name + "'", e);
        }
    }

} // class CMDIValidatorFactory
