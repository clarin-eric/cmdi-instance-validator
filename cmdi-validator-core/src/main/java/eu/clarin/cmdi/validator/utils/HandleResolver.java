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
package eu.clarin.cmdi.validator.utils;

import java.io.IOException;
import java.net.URI;
import java.util.HashSet;
import java.util.Set;

import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.client.utils.HttpClientUtils;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.clarin.cmdi.validator.CMDIValidatorException;

public class HandleResolver {
    private static final Logger logger =
            LoggerFactory.getLogger(HandleResolver.class);
    public final int ERROR   = -1;
    private final LRUCache<URI, Integer> cache =
            new LRUCache<URI, Integer>(16 * 1024);
    private final Set<URI> pending;
    private final Object waiter = new Object();
    private long cacheHits   = 0;
    private long cacheMisses = 0;


    public HandleResolver(final int threads) {
        this.pending = new HashSet<URI>(threads * 2);
    }


    public long getCacheHits() {
        return cacheHits;
    }


    public long getCacheMisses() {
        return cacheMisses;
    }


    public int resolve(final URI handle) throws CMDIValidatorException {
        if (handle == null) {
            throw new NullPointerException("handle == null");
        }
        logger.debug("resolving '{}'", handle);
        for (;;) {
            boolean doResolve = false;
            synchronized (cache) {
                final Integer cached = cache.get(handle);
                if (cached != null) {
                    logger.trace("got cached result for '{}': {}",
                            handle, cached);
                    cacheHits++;
                    return cached.intValue();
                }

                synchronized (pending) {
                    if (!pending.contains(handle)) {
                        cacheMisses++;
                        doResolve = true;
                        pending.add(handle);
                    }
                } // synchronized (pending)
            } // synchronized (cache)

            // either resolve in this thread of wait for pending resolve result
            if (doResolve) {
                int result = ERROR;
                try {
                    final HttpClient httpClient = newHttpClient();
                    try {
                        result = doResolve(handle, httpClient);
                    } finally {
                        HttpClientUtils.closeQuietly(httpClient);
                    }
                } catch (Throwable e) {
                    throw new CMDIValidatorException(
                            "error while resolving handle '" + handle + "'", e);
                } finally {
                    // cache result and notify other threads
                    synchronized (cache) {
                        logger.trace("caching result {} for '{}'",
                                result, handle);
                        cache.put(handle, Integer.valueOf(result));
                        synchronized (pending) {
                            pending.remove(handle);
                            synchronized (waiter) {
                                waiter.notifyAll();
                            } // synchronized (waiter)
                        } // synchronized (pending)
                    } // synchronized (cache)
                }
                return result;
            } else {
                try {
                    synchronized (waiter) {
                        waiter.wait();
                    } // synchronized (waiter)
                } catch (InterruptedException e) {
                    return ERROR;
                }
            }
        } // for
    }


    private int doResolve(final URI handle, final HttpClient httpClient)
            throws CMDIValidatorException {
        logger.trace("performing HTTP request for '{}'", handle);
        HttpHead request = null;
        HttpResponse response = null;
        try {
            request = new HttpHead(handle);
            response = httpClient.execute(request);

            final StatusLine status = response.getStatusLine();
            return status.getStatusCode();
        } catch (IOException e) {
            if (request != null) {
                request.abort();
            }
            throw new CMDIValidatorException("error resolving handle '" +
                    handle + "'", e);
        } finally {
            /* make sure to release allocated resources */
            HttpClientUtils.closeQuietly(response);
        }
    }


    private HttpClient newHttpClient() {
        final HttpClient client = new DefaultHttpClient();
        final HttpParams params = client.getParams();
        params.setParameter(CoreProtocolPNames.USER_AGENT,
                getClass().getName() + "/1.0");
        params.setBooleanParameter(ClientPNames.HANDLE_REDIRECTS, Boolean.TRUE);
        params.setBooleanParameter(ClientPNames.ALLOW_CIRCULAR_REDIRECTS, true);
        params.setIntParameter(ClientPNames.MAX_REDIRECTS, 16);
        return client;
    }

} // class HandleResolver
