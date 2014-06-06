package eu.clarin.cmdi.validator.utils;

import java.util.LinkedHashMap;
import java.util.Map;


@SuppressWarnings("serial")
public class LRUCache<A, B> extends LinkedHashMap<A, B> {
    private final int maxEntries;


    public LRUCache(final int maxEntries) {
        super(maxEntries + 1, 1.0f, true);
        this.maxEntries = maxEntries;
    }


    @Override
    protected boolean removeEldestEntry(final Map.Entry<A, B> eldest) {
        return super.size() > maxEntries;
    }

} // class LRUCache
