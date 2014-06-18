package eu.clarin.cmdi.validator;

import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.XdmNode;


public abstract class CMDIValidatorExtension {
    protected Processor processor;


    public final void initalize(final Processor processor)
            throws CMDIValidatorInitException {
        if (processor == null) {
            throw new NullPointerException("processor == null");
        }
        this.processor = processor;
        doInitialize();
    }


    public abstract void validate(XdmNode document,
            CMDIValidatorWriteableResult result) throws CMDIValidatorException;


    protected abstract void doInitialize() throws CMDIValidatorInitException;

} // CMDIValidatorExtension
