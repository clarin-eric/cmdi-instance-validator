package eu.clarin.cmdi.validator;

import net.sf.saxon.s9api.XdmNode;


public interface CMDIValidationPlugin {

    public void validate(XdmNode document, CMDIValidatorResultImpl result)
            throws CMDIValidatorException;

} // class CMDIValidationPlugin
