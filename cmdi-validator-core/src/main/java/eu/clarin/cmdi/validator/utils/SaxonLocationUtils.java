package eu.clarin.cmdi.validator.utils;

import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmNode;

public final class SaxonLocationUtils {
    
    private SaxonLocationUtils() {
    }


    public static int getLineNumber(final XdmItem item) {
        if ((item != null) && (item instanceof XdmNode)) {
            return ((XdmNode) item).getLineNumber();
        }
        return -1;
    }


    public static int getColumnNumber(final XdmItem item) {
        if ((item != null) && (item instanceof XdmNode)) {
            return ((XdmNode) item).getColumnNumber();
        }
        return -1;
    }

} // class SaxonLocationUtils
