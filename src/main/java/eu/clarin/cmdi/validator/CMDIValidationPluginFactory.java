package eu.clarin.cmdi.validator;

import net.sf.saxon.s9api.Processor;


public interface CMDIValidationPluginFactory {

    public CMDIValidationPlugin newInstance(Processor processor)
            throws CMDIValidatorInitException;

}
