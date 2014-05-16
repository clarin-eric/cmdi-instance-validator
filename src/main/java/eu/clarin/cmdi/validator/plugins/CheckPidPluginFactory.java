package eu.clarin.cmdi.validator.plugins;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.client.utils.HttpClientUtils;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XPathCompiler;
import net.sf.saxon.s9api.XPathExecutable;
import net.sf.saxon.s9api.XPathSelector;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmNode;
import eu.clarin.cmdi.validator.CMDIValidationPlugin;
import eu.clarin.cmdi.validator.CMDIValidationPluginFactory;
import eu.clarin.cmdi.validator.CMDIValidatorException;
import eu.clarin.cmdi.validator.CMDIValidatorInitException;
import eu.clarin.cmdi.validator.CMDIValidatorResultImpl;
import eu.clarin.cmdi.validator.utils.LRUCache;


public class CheckPidPluginFactory implements CMDIValidationPluginFactory {
    private static final String XPATH = "//*:ResourceProxy[*:ResourceType/text() = 'Resource' or *:ResourceType/text() = 'Metadata']/*:ResourceRef/text()";
    private static final String HDL_SCHEME = "hdl";
    private static final String HDL_PROXY_HTTP = "http";
    private static final String HDL_PROXY_HTTPS = "https";
    private static final String HDL_PROXY_HOST = "hdl.handle.net";
    private static final String URN_SCHEME = "urn";
    private static final Logger logger =
            LoggerFactory.getLogger(CheckPidPlugin.class);


    @Override
    public CMDIValidationPlugin newInstance(final Processor processor)
            throws CMDIValidatorInitException {
        return new CheckPidPlugin(processor);
    }

    public class CheckPidPlugin implements CMDIValidationPlugin {
        private final HttpClient httpClient;
        private final XPathExecutable xpath;
        private final LRUCache<URI, Integer> cache =
                new LRUCache<URI, Integer>(4*4096);

        private CheckPidPlugin(final Processor processor)
                throws CMDIValidatorInitException {
            this.httpClient = new DefaultHttpClient();

            final HttpParams params = this.httpClient.getParams();
            params.setParameter(CoreProtocolPNames.USER_AGENT,
                    CheckPidPlugin.class.getPackage().getName() + "/0.0.1");
            params.setBooleanParameter(ClientPNames.HANDLE_REDIRECTS, Boolean.TRUE);
            params.setBooleanParameter(ClientPNames.ALLOW_CIRCULAR_REDIRECTS, true);
            params.setIntParameter(ClientPNames.MAX_REDIRECTS, 16);

            try {
                final XPathCompiler compiler = processor.newXPathCompiler();
                this.xpath = compiler.compile(XPATH);
            } catch (SaxonApiException e) {
                throw new CMDIValidatorInitException(
                        "error initializing validation plugin", e);
            }

        }


        @Override
        public void validate(XdmNode document, CMDIValidatorResultImpl result)
                throws CMDIValidatorException {
            try {
                XPathSelector selector = xpath.load();
                selector.setContextItem(document);
                for (XdmItem item : selector) {
                    String handle = item.getStringValue();
                    if (handle != null) {
                        handle = handle.trim();
                        if (handle.isEmpty()) {
                            handle = null;
                        }
                    }

                    if (handle != null) {
                        checkHandle(handle, result);
                    } else {
                        result.reportError(-1, -1,
                                "invalid handle (<ResourceRef> was empty)");
                    }
                }
            } catch (SaxonApiException e) {
                throw new CMDIValidatorException("failed to check handles", e);
            }
        }


        private void checkHandle(String handle, CMDIValidatorResultImpl result)
                throws CMDIValidatorException {
            try {
                URI uri = new URI(handle);
                if (HDL_SCHEME.equalsIgnoreCase(uri.getScheme())) {
                    String path = uri.getSchemeSpecificPart();
                    if (!path.startsWith("/")) {
                        path = "/" + path;
                    }
                    try {
                        checkHandle2(new URI(HDL_PROXY_HTTP, HDL_PROXY_HOST,
                                path, null), result);
                    } catch (URISyntaxException e) {
                        throw new CMDIValidatorException(
                                "created an invalid URI", e);
                    }
                } else if (URN_SCHEME.equals(uri.getScheme())) {
                    result.reportInfo(-1, -1, "PID '" + handle +
                            "' skipped, because URN resolving is not supported");
                } else if (HDL_PROXY_HTTP.equalsIgnoreCase(uri.getScheme()) ||
                        HDL_PROXY_HTTPS.equalsIgnoreCase(uri.getScheme())) {
                    if (HDL_PROXY_HOST.equalsIgnoreCase(uri.getHost())) {
                        checkHandle2(uri, result);
                    } else {
                        result.reportError(-1, -1,
                                "PID '" + handle +
                                "' contains an unexpected host part in the URI: " +
                                uri.getHost());
                    }
                } else {
                    result.reportError(-1, -1,
                            "PID '" + handle +
                            "' contains an unexpected schema part in the URI: " +
                            uri.getScheme());
                }
            } catch (URISyntaxException e) {
                result.reportError(-1, -1, "PID '" + handle +
                        "' is not a well-formed URI: " + e.getMessage());
            }
        }


        private void checkHandle2(URI uri, CMDIValidatorResultImpl result)
                throws CMDIValidatorException {
            Integer code = cache.get(uri);
            if (code == null) {
                code = checkHandle3(uri, result);
                // cache result
                cache.put(uri, code);
            }

            switch (code) {
            case HttpStatus.SC_OK:
                /* no special message in this case */
                break;
            case HttpStatus.SC_UNAUTHORIZED:
                /* FALL-THROUGH */
            case HttpStatus.SC_FORBIDDEN:
                result.reportInfo(-1, -1, "PID '" + uri +
                        "' resolved to an access protected resource (" +
                        code + ")");
                break;
            case HttpStatus.SC_NOT_FOUND:
                result.reportError(-1, -1, "PID '" + uri +
                        "' resolved to an non-existing resource (" +
                        code + ")");
                break;
            default:
                result.reportError(-1, -1, "PID '" + uri +
                        "' resolved with an unexpected result (" +
                        code + ")");
                break;
            }
        }


        private int checkHandle3(URI uri, CMDIValidatorResultImpl result)
                throws CMDIValidatorException {
            HttpHead request = null;
            HttpResponse response = null;
            try {
                request = new HttpHead(uri);
                response = httpClient.execute(request);

                final StatusLine status = response.getStatusLine();
                logger.debug("checked handle: file={}, uri={}, status={}",
                        result.getFile(), uri, status.getStatusCode());
                return status.getStatusCode();
            } catch (IOException e) {
                throw new CMDIValidatorException("error performing handle check", e);
            } finally {
                if (response != null) {
                    try {
                        EntityUtils.consume(response.getEntity());
                    } catch (IOException ex) {
                        /* IGNORE */
                    }

                    /* make sure to release allocated resources */
                    HttpClientUtils.closeQuietly(response);
                }
            }
        }
    }

} // class CheckHandlePluginFactory
