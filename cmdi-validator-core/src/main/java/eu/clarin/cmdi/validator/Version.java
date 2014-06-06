package eu.clarin.cmdi.validator;


public final class Version {

    private Version() {
    }


    public static String getVersion() {
        String version = Version.class.getPackage().getImplementationVersion();
        return (version != null) ? version : "[UNKNOWN]";
    }

} // class Version
