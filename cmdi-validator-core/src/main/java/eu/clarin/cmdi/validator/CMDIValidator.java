package eu.clarin.cmdi.validator;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.transform.dom.DOMSource;

import net.sf.saxon.s9api.Axis;
import net.sf.saxon.s9api.DocumentBuilder;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.WhitespaceStrippingPolicy;
import net.sf.saxon.s9api.XPathCompiler;
import net.sf.saxon.s9api.XPathExecutable;
import net.sf.saxon.s9api.XPathSelector;
import net.sf.saxon.s9api.XdmDestination;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmSequenceIterator;
import net.sf.saxon.s9api.XsltExecutable;
import net.sf.saxon.s9api.XsltTransformer;

import org.apache.xerces.impl.xs.XMLSchemaLoader;
import org.apache.xerces.impl.xs.XSDDescription;
import org.apache.xerces.parsers.DOMParser;
import org.apache.xerces.parsers.XML11Configuration;
import org.apache.xerces.util.SymbolTable;
import org.apache.xerces.xni.XMLResourceIdentifier;
import org.apache.xerces.xni.XNIException;
import org.apache.xerces.xni.grammars.Grammar;
import org.apache.xerces.xni.grammars.XMLGrammarDescription;
import org.apache.xerces.xni.grammars.XMLGrammarPool;
import org.apache.xerces.xni.parser.XMLEntityResolver;
import org.apache.xerces.xni.parser.XMLErrorHandler;
import org.apache.xerces.xni.parser.XMLInputSource;
import org.apache.xerces.xni.parser.XMLParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import eu.clarin.cmdi.validator.CMDIValidatorResult.Severity;
import eu.clarin.cmdi.validator.utils.LRUCache;


public final class CMDIValidator {
    private static final Logger logger =
            LoggerFactory.getLogger(CMDIValidator.class);
    private static final String XML_SCHEMA_LOCATION =
            "http://www.w3.org/2001/xml.xsd";
    private static final String XML_SCHEMA_GRAMMAR_TYPE =
            "http://www.w3.org/2001/XMLSchema";
    private static final String GRAMMAR_POOL =
            "http://apache.org/xml/properties/internal/grammar-pool";
    private static final String NAMESPACES_FEATURE_ID =
            "http://xml.org/sax/features/namespaces";
    private static final String VALIDATION_FEATURE_ID =
            "http://xml.org/sax/features/validation";
    private static final String SCHEMA_VALIDATION_FEATURE_ID =
            "http://apache.org/xml/features/validation/schema";
    private static final String SCHEMA_FULL_CHECKING_FEATURE_ID =
            "http://apache.org/xml/features/validation/schema-full-checking";
    private static final String HONOUR_ALL_SCHEMA_LOCATIONS_ID =
            "http://apache.org/xml/features/honour-all-schemaLocations";
    private static final int INITAL_SYMBOL_TABLE_SIZE = 16141;
    private static final String SVRL_NAMESPACE_URI = "http://purl.oclc.org/dsdl/svrl";
    private static final String SVRL_NAMESPACE_PREFIX = "svrl";
    private static final QName SVRL_TEXT =
            new QName(SVRL_NAMESPACE_URI, "text");
    private static final QName SVRL_FAILED_ASSERT =
            new QName(SVRL_NAMESPACE_URI, "failed-assert");
    private final Processor processor;
    private final XPathExecutable xpath1;
    private final XPathExecutable xpath2;
    private final XsltTransformer schematronValidator;
    private final XML11Configuration config;
    private final DocumentBuilder builder;
    private final List<CMDIValidationPlugin> plugins;
    private final CMDIValidatorResultImpl result;


    CMDIValidator(final Processor processor,
            final SchemaLoader schemaLoader,
            XsltExecutable schematronExecutable,
            List<CMDIValidationPlugin> plugins)
            throws CMDIValidatorInitException {
        if (processor == null) {
            throw new NullPointerException("processor == null");
        }
        this.processor = processor;
        if (schematronExecutable != null) {
            try {
                final XPathCompiler compiler = processor.newXPathCompiler();
                compiler.declareNamespace(SVRL_NAMESPACE_PREFIX, SVRL_NAMESPACE_URI);
                this.xpath1 = compiler.compile("//(svrl:failed-assert|svrl:successful-report)");
                this.xpath2 = compiler.compile("preceding-sibling::svrl:fired-rule/@role");
                this.schematronValidator = schematronExecutable.load();
            } catch (SaxonApiException e) {
                throw new CMDIValidatorInitException(
                        "error initializing validator", e);
            }
        } else {
            this.xpath1              = null;
            this.xpath2              = null;
            this.schematronValidator = null;
        }

        try {
            /*
             * initialize Xerces
             */
            XMLEntityResolver resolver = new XMLEntityResolver() {
                @Override
                public XMLInputSource resolveEntity(
                        XMLResourceIdentifier identifier) throws XNIException,
                        IOException {
                    final String uri = identifier.getExpandedSystemId();
                    if (uri == null) {
                        throw new IOException("bad schema location for namespace '" +
                                        identifier.getNamespace() + "': " +
                                        identifier.getLiteralSystemId());
                    }
                    InputStream stream = schemaLoader.loadSchemaFile(
                            identifier.getNamespace(), uri);
                    return new XMLInputSource(null, null, null, stream, null);
                }
            };

            SymbolTable symbols = new SymbolTable(INITAL_SYMBOL_TABLE_SIZE);
            ShadowCacheXMLGrammarPool pool = new ShadowCacheXMLGrammarPool(8);

            XMLSchemaLoader xsdLoader = new XMLSchemaLoader(symbols);
            xsdLoader.setParameter(GRAMMAR_POOL, pool);
            xsdLoader.setEntityResolver(resolver);
            xsdLoader.setErrorHandler(new XMLErrorHandler() {
                @Override
                public void warning(String domain, String key,
                        XMLParseException e) throws XNIException {
                    /* ignore warnings */
                }

                @Override
                public void error(String domain, String key, XMLParseException e)
                        throws XNIException {
                    throw e;
                }

                @Override
                public void fatalError(String domain, String key,
                        XMLParseException e) throws XNIException {
                    throw e;
                }
            });

            InputStream stream = null;
            try {
                stream = schemaLoader.loadSchemaFile(XMLConstants.XML_NS_URI,
                        XML_SCHEMA_LOCATION);
                Grammar grammar = xsdLoader.loadGrammar(
                        new XMLInputSource(XMLConstants.XML_NS_URI,
                                XML_SCHEMA_LOCATION, null, stream, null));
                if (grammar != null) {
                    pool.cacheGrammars(XML_SCHEMA_GRAMMAR_TYPE,
                            new Grammar[] { grammar });
                }
                pool.lockPool();
            } finally {
                if (stream != null) {
                    stream.close();
                }
            }

            config = new XML11Configuration(symbols, pool);
            config.setFeature(NAMESPACES_FEATURE_ID, true);
            config.setFeature(VALIDATION_FEATURE_ID, true);
            config.setFeature(SCHEMA_VALIDATION_FEATURE_ID, true);
            config.setFeature(SCHEMA_FULL_CHECKING_FEATURE_ID, true);
            config.setFeature(HONOUR_ALL_SCHEMA_LOCATIONS_ID, true);
            config.setEntityResolver(resolver);
            config.setErrorHandler(new XMLErrorHandler() {
                @Override
                public void warning(String domain, String key,
                        XMLParseException e) throws XNIException {
                    reportWarning(e.getLineNumber(), e.getColumnNumber(),
                            e.getMessage(), e);
                }

                @Override
                public void error(String domain, String key, XMLParseException e)
                        throws XNIException {
                    reportError(e.getLineNumber(), e.getColumnNumber(),
                            e.getMessage(), e);
                    throw e;
                }

                @Override
                public void fatalError(String domain, String key,
                        XMLParseException e) throws XNIException {
                    reportError(e.getLineNumber(), e.getColumnNumber(),
                            e.getMessage(), e);
                    throw e;
                }
            });

            /*
             * initialize Saxon document builder
             */
            this.builder = this.processor.newDocumentBuilder();
            this.builder.setWhitespaceStrippingPolicy(
                    WhitespaceStrippingPolicy.IGNORABLE);

            /*
             * initialize plugins
             */
            if ((plugins != null) && !plugins.isEmpty()) {
                this.plugins = plugins;
            } else {
                this.plugins = null;
            }

            /*
             * initialize other stuff
             */
            this.result =  new CMDIValidatorResultImpl();
        } catch (IOException e) {
            throw new CMDIValidatorInitException("initialization failed", e);
        }
    }


    public void validate(InputStream stream, File file,
            CMDIValidatorJobHandler handler) throws CMDIValidatorException {
        if (stream == null) {
            throw new NullPointerException("stream == null");
        }

        try {
            result.setFile(file);

            /*
             * step 1: parse document and perform schema validation
             */
            final XdmNode document = parseInstance(stream);

            if (document != null) {
                /*
                 * step 2: perform Schematron validation
                 */
                if (schematronValidator != null) {
                    validateSchematron(document);
                }

                /*
                 * step 3: run plugins, if any
                 */
                if (plugins != null) {
                    for (CMDIValidationPlugin plugin : plugins) {
                        plugin.validate(document, result);
                    }
                }
            } else {
                logger.debug("parseInstance() returned no result");
            }
        } catch (CMDIValidatorException e) {
            throw e;
        } finally {
            if (handler != null) {
                if (result.isHighestSeverity(Severity.ERROR)) {
                    handler.onValidationFailure(result);
                } else {
                    handler.onValidationSuccess(result);
                }
            }
            result.reset();
        }
    }


    private XdmNode parseInstance(InputStream stream)
            throws CMDIValidatorException {
        try {
            final DOMParser parser = new DOMParser(config);
            try {
                parser.parse(new InputSource(stream));
                stream.close();

                final Document dom = parser.getDocument();
                if (dom == null) {
                    throw new CMDIValidatorException(
                            "parser returned no return result");
                }
                return builder.build(new DOMSource(dom));
            } finally {
                parser.reset();
            }
        } catch (XNIException e) {
            throw new CMDIValidatorException(e.getMessage(), e);
        } catch (SaxonApiException e) {
            throw new CMDIValidatorException(e.getMessage(), e);
        } catch (SAXException e) {
            throw new CMDIValidatorException(e.getMessage(), e);
        } catch (IOException e) {
            throw new CMDIValidatorException(e.getMessage(), e);
        } finally {
            /* make sure, steam is closed */
            try {
                stream.close();
            } catch (IOException e) {
                /* IGNORE */
            }
        }
    }


    private void validateSchematron(XdmNode document)
            throws CMDIValidatorException {
        try {
            logger.debug("performing schematron validation ...");
            schematronValidator.setSource(document.asSource());
            final XdmDestination destination = new XdmDestination();
            schematronValidator.setDestination(destination);
            schematronValidator.transform();


            XPathSelector selector = xpath1.load();
            selector.setContextItem(destination.getXdmNode());
            for (XdmItem item : selector) {
                final XdmNode node = (XdmNode) item;
                final XdmNode text = getFirstChild(node, SVRL_TEXT);
                String msg = (text != null) ? text.getStringValue().trim() : null;
                if (SVRL_FAILED_ASSERT.equals(node.getNodeName())) {
                    XPathSelector selector2 = xpath2.load();
                    String role = null;
                    selector2.setContextItem(node);
                    XdmItem evaluateSingle = selector2.evaluateSingle();
                    if (evaluateSingle != null) {
                        role = evaluateSingle.getStringValue().trim();
                    }
                    if ("warning".equalsIgnoreCase(role)) {
                        result.reportWarning(-1, -1, msg);
                    } else {
                        result.reportError(-1, -1, msg);
                    }
                } else {
                    result.reportInfo(-1, -1, msg);
                }
            }
        } catch (SaxonApiException e) {
            throw new CMDIValidatorException(
                    "error performing schematron validation", e);
        }
    }


    private static XdmNode getFirstChild(XdmNode parent, QName name) {
        XdmSequenceIterator i = parent.axisIterator(Axis.CHILD, name);
        if (i.hasNext()) {
            return (XdmNode) i.next();
        } else {
            return null;
        }
    }


    private void reportWarning(int line, int col, String message, Throwable cause) {
        logger.debug("reporting warning: [{}:{}]: {}", line, col, message);
        if (result != null) {
            result.reportWarning(line, col, message, cause);
        }
    }


    private void reportError(int line, int col, String message, Throwable cause) {
        logger.debug("reporting error: [{}:{}]: {}", line, col, message);
        if (result != null) {
            result.reportError(line, col, message, cause);
        }
    }


    private static class ShadowCacheXMLGrammarPool implements XMLGrammarPool {
        private final Set<Grammar> cache =
                new LinkedHashSet<Grammar>();
        private final Map<String, Grammar> shadowCache;
        private boolean locked = false;


        private ShadowCacheXMLGrammarPool(int shadowCacheSize) {
            this.shadowCache = new LRUCache<String, Grammar>(shadowCacheSize);
        }


        @Override
        public Grammar[] retrieveInitialGrammarSet(String grammarType) {
            if (XML_SCHEMA_GRAMMAR_TYPE.equals(grammarType) &&
                    !cache.isEmpty()) {
                final Grammar[] result = new Grammar[cache.size()];
                return cache.toArray(result);
            } else {
                return null;
            }
        }


        @Override
        public Grammar retrieveGrammar(XMLGrammarDescription d) {
            logger.debug("search for grammar: {} / {} / {} / {}",
                    d.getNamespace(),
                    d.getLiteralSystemId(),
                    d.getExpandedSystemId(),
                    d.getBaseSystemId());
            if ((d.getNamespace() == null) || !(d instanceof XSDDescription)) {
                logger.debug("-> miss (invalid arguments supplied by caller)");
                return null;
            }

            final XSDDescription desc = (XSDDescription) d;
            final String namespace = desc.getNamespace();
            Grammar result = findGrammerFromCache(desc);
            if (result != null) {
                logger.debug("-> match from cache: {} -> {} / {}",
                        namespace,
                        desc.getNamespace(),
                        desc.getLiteralSystemId());
                return result;
            }

            String locationHint = null;
            if (desc.getLocationHints() != null) {
                String[] h = desc.getLocationHints();
                if (h.length > 0) {
                    locationHint = h[0];
                }
                logger.debug("-> hint: {}", locationHint);
            } else if (desc.getLiteralSystemId() != null) {
                locationHint = desc.getLiteralSystemId();
            }

            if (locationHint != null) {
                Grammar grammar = shadowCache.get(locationHint);
                if (grammar != null) {
                    logger.debug("-> match from shadow cache: {} -> {}",
                            grammar.getGrammarDescription().getNamespace(),
                            locationHint);
                    return grammar;
                }
            }
            logger.debug("-> miss");
            return null;
        }


        @Override
        public void lockPool() {
            locked = true;
        }


        @Override
        public void unlockPool() {
            locked = false;
        }


        @Override
        public void clear() {
            if (!locked) {
                cache.clear();
            }
        }


        @Override
        public void cacheGrammars(String grammarType, Grammar[] grammars) {
            if (XML_SCHEMA_GRAMMAR_TYPE.equals(grammarType) &&
                    (grammars != null) &&
                    (grammars.length > 0)) {
                for (Grammar grammar : grammars) {
                    final XMLGrammarDescription gd =
                            grammar.getGrammarDescription();
                    if (findGrammerFromCache(gd) == null) {
                        if (!locked) {
                            logger.debug("cached grammar: {} / {}",
                                    gd.getNamespace(),
                                    gd.getLiteralSystemId());
                            cache.add(grammar);
                        } else {
                            final String literalSystemId =
                                        gd.getLiteralSystemId();
                            if (!shadowCache.containsKey(literalSystemId)) {
                                logger.debug("shadow cached grammar: {} / {}",
                                        gd.getNamespace(),
                                        gd.getLiteralSystemId());
                                shadowCache.put(literalSystemId, grammar);
                            }
                        }
                    }
                } // for
            }
        }


        private Grammar findGrammerFromCache(XMLGrammarDescription desc) {
            if (!cache.isEmpty()) {
                for (Grammar grammar : cache) {
                    final XMLGrammarDescription gd =
                            grammar.getGrammarDescription();
                    if (gd.getNamespace().equals(desc.getNamespace())) {
                        return grammar;
                    }
                }
            }
            return null;
        }
    }  // class ShadowCacheGrammarPool

} // class CMDIValidator
