package eu.clarin.cmdi.validator;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import javax.xml.XMLConstants;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.client.utils.HttpClientUtils;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CMDISchemaLoader {
    public static final long DISABLE_CACHE_AGING = -1;
    private static final Logger logger =
            LoggerFactory.getLogger(CMDISchemaLoader.class);
    private static final String XML_XSD_RESSOURCE = "/xml.xsd";
    private final File cacheDirectory;
    private final long maxCacheAge;
    private final HttpClient httpClient;


    public CMDISchemaLoader(File cacheDirectory, long maxCacheAge) {
        if (cacheDirectory == null) {
            throw new NullPointerException("cacheDirectory == null");
        }
        if (maxCacheAge < -1) {
            throw new IllegalArgumentException("maxCacheAge < -1");
        }
        this.cacheDirectory = cacheDirectory;
        this.maxCacheAge    = maxCacheAge;
        this.httpClient     = new DefaultHttpClient();

        final HttpParams params = this.httpClient.getParams();
        params.setParameter(CoreProtocolPNames.USER_AGENT,
                this.getClass().getPackage().getName() + "/0.0.1");
        params.setBooleanParameter(ClientPNames.HANDLE_REDIRECTS, Boolean.TRUE);
        params.setBooleanParameter(ClientPNames.ALLOW_CIRCULAR_REDIRECTS, true);
        params.setIntParameter(ClientPNames.MAX_REDIRECTS, 16);
    }


    public CMDISchemaLoader(File cacheDirectory) {
        this(cacheDirectory, DISABLE_CACHE_AGING);
    }


    public synchronized InputStream loadSchemaFile(String targetNamespace,
            String schemaLocation) throws IOException {
        if (targetNamespace == null) {
            throw new NullPointerException("targetNamespace == null");
        }
        if (schemaLocation == null) {
            throw new NullPointerException("schemaLocation == null");
        }

        logger.trace("loading schema: targetNamespace={}, location={}",
                targetNamespace, schemaLocation);
        InputStream stream = null;
        if (XMLConstants.XML_NS_URI.equalsIgnoreCase(targetNamespace)) {
            stream = this.getClass().getResourceAsStream(XML_XSD_RESSOURCE);
            if (stream != null) {
                logger.trace("using bundled schema for '{}'", schemaLocation);
                return stream;
            }
            logger.warn("unable to load bundled schema for '{}', " +
                    "falling back to download.", schemaLocation);
        }

        // fall back to file cache ...
        final File cacheFile = makeCacheFile(schemaLocation);
        if (cacheFile.exists()) {
            final long age =
                    System.currentTimeMillis() - cacheFile.lastModified();
            if ((maxCacheAge != DISABLE_CACHE_AGING) && (age > maxCacheAge)) {
                logger.trace("-> cached file '{}' expired", cacheFile);
                cacheFile.delete();
            } else {
                logger.trace("-> from file cache");
                return new FileInputStream(cacheFile);
            }
        }

        try {
            logger.debug("downloading schema from '{}'", schemaLocation);
            final URI uri = new URI(schemaLocation);
            final HttpResponse response = executeRequest(uri);
            final HttpEntity entity = response.getEntity();
            if (entity == null) {
                throw new IOException("the request returned no message body");
            }

            try {
                final InputStream in = entity.getContent();

                final FileOutputStream out =
                        new FileOutputStream(cacheFile);
                int read;
                final byte[] buffer = new byte[4096];
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                out.close();

                return new FileInputStream(cacheFile);
            } catch (IllegalStateException e) {
                throw new IOException("error reading response", e);
            } catch (IOException e) {
                /* delete broken cache file */
                if (cacheFile != null) {
                    cacheFile.delete();
                }
                throw e;
            } finally {
                /* make sure to release allocated resources */
                HttpClientUtils.closeQuietly(response);
            }
        } catch (URISyntaxException e) {
            throw new IOException("schemaLocation uri is invalid: " +
                    schemaLocation, e);
        }
    }


    private File makeCacheFile(String schemaLocation) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < schemaLocation.length(); i++) {
            final char c = schemaLocation.charAt(i);
            switch (c) {
            case '.':
                /* FALL-THROUGH */
            case ':':
                /* FALL-THROUGH */
            case ';':
                /* FALL-THROUGH */
            case '?':
                /* FALL-THROUGH */
            case '&':
                /* FALL-THROUGH */
            case '=':
                /* FALL-THROUGH */
            case '"':
                /* FALL-THROUGH */
            case '\'':
                /* FALL-THROUGH */
            case '/':
                /* FALL-THROUGH */
            case '\\':
                sb.append('_');
                break;
            default:
                sb.append(c);
            }
        } // for
        sb.append(".xsd");
        return new File(cacheDirectory, sb.toString());
    }


    private HttpResponse executeRequest(URI uri) throws IOException {
        HttpGet request = null;
        HttpResponse response = null;
        try {
            logger.trace("submitting HTTP request: {}", uri.toString());
            request = new HttpGet(uri);
            response = httpClient.execute(request);
            StatusLine status = response.getStatusLine();
            if (status.getStatusCode() != HttpStatus.SC_OK) {
                if (status.getStatusCode() == HttpStatus.SC_NOT_FOUND) {
                    throw new IOException("not found: " + uri);
                } else {
                    throw new IOException("unexpected status: " +
                            status.getStatusCode());
                }
            }
            return response;
        } catch (IOException e) {
            /*
             * if an error occurred, make sure we are freeing up the resources
             * we've used
             */
            if (response != null) {
                try {
                    EntityUtils.consume(response.getEntity());
                } catch (IOException ex) {
                    /* IGNORE */
                }

                /* make sure to release allocated resources */
                HttpClientUtils.closeQuietly(response);
            }
            if (request != null) {
                request.abort();
            }
            throw e;
        }
    }

} // class CMDISchemaLoader
