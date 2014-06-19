package eu.clarin.cmdi.validator;

import java.io.File;


public class CMDIValidatorFactoryConfig {
    private File schemaCacheDirectory = null;
    private File schematronSchemaFile = null;
    private boolean schematronDisabled = false;


    public File getSchemaCacheDirectory() {
        return schemaCacheDirectory;
    }


    public File getSchematronSchemaFile() {
        return schematronSchemaFile;
    }


    public boolean isSchematronDisabled() {
        return schematronDisabled;
    }


    public static class Builder {
        private final CMDIValidatorFactoryConfig config =
                new CMDIValidatorFactoryConfig();


        public Builder schemaCacheDirectory(File schemaCacheDirectory) {
            if ((schemaCacheDirectory != null) &&
                    !schemaCacheDirectory.isDirectory()) {
                throw new IllegalArgumentException("'" + schemaCacheDirectory +
                        "'is not a directory");
            }
            config.schemaCacheDirectory = schemaCacheDirectory;
            return this;
        }


        public Builder schematronSchemaFile(File schematronSchemaFile) {
            if ((schematronSchemaFile != null) &&
                    !schematronSchemaFile.isFile()) {
                throw new IllegalArgumentException("'" + schematronSchemaFile +
                        "'is not a regular file");
            }
            config.schematronSchemaFile = schematronSchemaFile;
            return this;
        }


        public Builder schematronDisabled(boolean schematronDisabled) {
            config.schematronDisabled = schematronDisabled;
            return this;
        }


        public Builder disableSchematron() {
            config.schematronDisabled = true;
            return this;
        }


        public CMDIValidatorFactoryConfig build() {
            return config;
        }
    } // class Builder

} // CMDIValidatorFactoryConfig
