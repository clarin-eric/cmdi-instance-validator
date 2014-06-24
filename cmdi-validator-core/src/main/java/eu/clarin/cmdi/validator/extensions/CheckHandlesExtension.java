/**
 * This software is copyright (c) 2014 by
 *  - Institut fuer Deutsche Sprache (http://www.ids-mannheim.de)
 * This is free software. You can redistribute it
 * and/or modify it under the terms described in
 * the GNU General Public License v3 of which you
 * should have received a copy. Otherwise you can download
 * it from
 *
 *   http://www.gnu.org/licenses/gpl-3.0.txt
 *
 * @copyright Institut fuer Deutsche Sprache (http://www.ids-mannheim.de)
 *
 * @license http://www.gnu.org/licenses/gpl-3.0.txt
 *  GNU General Public License v3
 */
package eu.clarin.cmdi.validator.extensions;

import java.net.URI;
import java.net.URISyntaxException;

import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XPathCompiler;
import net.sf.saxon.s9api.XPathExecutable;
import net.sf.saxon.s9api.XPathSelector;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmNode;
import eu.clarin.cmdi.validator.CMDIValidatorException;
import eu.clarin.cmdi.validator.CMDIValidatorExtension;
import eu.clarin.cmdi.validator.CMDIValidatorInitException;
import eu.clarin.cmdi.validator.CMDIValidatorWriteableResult;
import eu.clarin.cmdi.validator.utils.HandleResolver;

public class CheckHandlesExtension extends CMDIValidatorExtension {
    private static final String XPATH = "//*:ResourceProxy[*:ResourceType/text() = 'Resource' or *:ResourceType/text() = 'Metadata']/*:ResourceRef/text()";
    private static final String HDL_SCHEME = "hdl";
    private static final String HDL_PROXY_HTTP = "http";
    private static final String HDL_PROXY_HTTPS = "https";
    private static final String HDL_PROXY_HOST = "hdl.handle.net";
    private static final String URN_SCHEME = "urn";
    @SuppressWarnings("unused")
    private static final Logger logger =
            LoggerFactory.getLogger(CheckHandlesExtension.class);
    private final int threads;
    private final boolean resolveHandles;
    private HandleResolver resolver = null;
    private XPathExecutable xpath;


    public CheckHandlesExtension(int threads, boolean resolveHandles) {
        if (threads < 1) {
            throw new IllegalArgumentException("threads < 1");
        }
        this.threads        = threads;
        this.resolveHandles = resolveHandles;
    }


    public CheckHandlesExtension(boolean resolveHandles) {
        this(Runtime.getRuntime().availableProcessors(), resolveHandles);
    }


    @Override
    protected void doInitialize() throws CMDIValidatorInitException {
        if (resolveHandles) {
            this.resolver = new HandleResolver(threads);
        }

        try {
            final XPathCompiler compiler = processor.newXPathCompiler();
            this.xpath = compiler.compile(XPATH);
        } catch (SaxonApiException e) {
            throw new CMDIValidatorInitException(
                    "error initializing check handle extension", e);
        }
    }


    @Override
    public void validate(XdmNode document, CMDIValidatorWriteableResult result)
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
                    checkHandleURISyntax(handle, result);
                } else {
                    result.reportError(-1, -1,
                            "invalid handle (<ResourceRef> was empty)");
                }
            }
        } catch (SaxonApiException e) {
            throw new CMDIValidatorException("failed to check handles", e);
        }
    }


    private void checkHandleURISyntax(String handle,
            CMDIValidatorWriteableResult result) throws CMDIValidatorException {
        try {
            final URI uri = new URI(handle);
            if (HDL_SCHEME.equalsIgnoreCase(uri.getScheme())) {
                String path = uri.getSchemeSpecificPart();
                if (!path.startsWith("/")) {
                    path = "/" + path;
                }
                try {
                    final URI actionableURI =
                            new URI(HDL_PROXY_HTTP, HDL_PROXY_HOST, path, null);
                    checkHandleResolves(actionableURI, result);
                } catch (URISyntaxException e) {
                    throw new CMDIValidatorException(
                            "created an invalid URI", e);
                }
            } else if (URN_SCHEME.equals(uri.getScheme())) {
                if (resolveHandles) {
                    result.reportInfo(-1, -1, "PID '" + handle +
                            "' skipped, because URN resolving is not supported");
                } else {
                    result.reportInfo(-1, -1, "PID '" + handle +
                            "' skipped, because URN sytax checking is not supported");
                }
            } else if (HDL_PROXY_HTTP.equalsIgnoreCase(uri.getScheme()) ||
                    HDL_PROXY_HTTPS.equalsIgnoreCase(uri.getScheme())) {
                if (HDL_PROXY_HOST.equalsIgnoreCase(uri.getHost())) {
                    checkHandleResolves(uri, result);
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


    private void checkHandleResolves(URI uri,
            CMDIValidatorWriteableResult result) throws CMDIValidatorException {
        if (resolver != null) {
            int code = resolver.resolve(uri);
            switch (code) {
            case HttpStatus.SC_OK:
                /* no special message in this case */
                break;
            case HttpStatus.SC_UNAUTHORIZED:
                /* FALL-THROUGH */
            case HttpStatus.SC_FORBIDDEN:
                result.reportInfo(-1, -1, "PID '" + uri +
                        "' resolved to an access protected resource (" + code +
                        ")");
                break;
            case HttpStatus.SC_NOT_FOUND:
                result.reportError(-1, -1, "PID '" + uri +
                        "' resolved to an non-existing resource (" + code + ")");
                break;
            default:
                result.reportError(-1, -1, "PID '" + uri +
                        "' resolved with an unexpected result (" + code + ")");
                break;
            }
        }
    }

} // CheckHandleExtension
