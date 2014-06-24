package eu.clarin.cmdi.validator;

public interface CMDIValidatorProcessor {

    public void process(final CMDIValidator validator)
            throws CMDIValidatorException;

} // interface CMDIValidatorProcessor
