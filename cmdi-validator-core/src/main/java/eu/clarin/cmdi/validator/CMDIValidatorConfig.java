package eu.clarin.cmdi.validator;

import java.io.File;
import java.io.FileFilter;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;


public class CMDIValidatorConfig {
    private final File root;
    private final CMDIValidatorHandler handler;
    private FileFilter fileFilter = null;
    private File schemaCacheDirectory = null;
    private CMDISchemaLoader schemaLoader = null;
    private File schematronSchemaFile = null;
    private boolean schematronDisabled = false;
    private List<CMDIValidatorExtension> extensions = null;


    private CMDIValidatorConfig(final File root,
            final CMDIValidatorHandler handler) {
        if (root == null) {
            throw new NullPointerException("root = null");
        }
        if (handler == null) {
            throw new NullPointerException("handler == null");
        }
        this.root    = root;
        this.handler = handler;
    }


    public File getRoot() {
        return root;
    }


    public FileFilter getFileFilter() {
        return fileFilter;
    }


    public CMDIValidatorHandler getHandler() {
        return handler;
    }


    public File getSchemaCacheDirectory() {
        return schemaCacheDirectory;
    }


    public CMDISchemaLoader getSchemaLoader() {
        return schemaLoader;
    }


    public File getSchematronSchemaFile() {
        return schematronSchemaFile;
    }


    public boolean isSchematronDisabled() {
        return schematronDisabled;
    }


    public List<CMDIValidatorExtension> getExtensions() {
        if (extensions != null) {
            return Collections.unmodifiableList(extensions);
        } else {
            return null;
        }
    }


    public static class Builder {
        private final CMDIValidatorConfig config;


        public Builder(final File root, final CMDIValidatorHandler handler) {
            if (root == null) {
                throw new NullPointerException("root == null");
            }
            if (handler == null) {
                throw new NullPointerException("handler == null");
            }
            this.config = new CMDIValidatorConfig(root, handler);
        }


        public Builder fileFilter(final FileFilter fileFilter) {
            config.fileFilter = fileFilter;
            return this;
        }


        public Builder schemaCacheDirectory(final File schemaCacheDirectory) {
            if (schemaCacheDirectory == null) {
                throw new NullPointerException("schemaCacheDirectory == null");
            }
            if (!schemaCacheDirectory.isDirectory()) {
                throw new IllegalArgumentException("'" + schemaCacheDirectory +
                        "'is not a directory");
            }
            config.schemaCacheDirectory = schemaCacheDirectory;
            return this;
        }


        public Builder schemaLoader(final CMDISchemaLoader schemaLoader) {
            if (schemaLoader == null) {
                throw new NullPointerException("schemaLoader == null");
            }
            config.schemaLoader = schemaLoader;
            return this;
        }


        public Builder schematronSchemaFile(final File schematronSchemaFile) {
            if (schematronSchemaFile == null) {
                throw new NullPointerException("schematronSchemaFile == null");
            }
            if (!schematronSchemaFile.isFile()) {
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


        public Builder extension(final CMDIValidatorExtension extension) {
            if (extension == null) {
                throw new NullPointerException("extension == null");
            }
            if (config.extensions == null) {
                config.extensions = new LinkedList<CMDIValidatorExtension>();
            }
            config.extensions.add(extension);
            return this;
        }


        public CMDIValidatorConfig build() {
            return config;
        }

    } // class Builder

} // CMDIValidatorFactoryConfig
