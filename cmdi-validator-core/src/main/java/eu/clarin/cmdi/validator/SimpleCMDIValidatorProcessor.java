package eu.clarin.cmdi.validator;

public class SimpleCMDIValidatorProcessor implements CMDIValidatorProcessor {

    @Override
    public void process(final CMDIValidator validator)
            throws CMDIValidatorException {
        if (validator == null) {
            throw new NullPointerException("validator == null");
        }

        for (;;) {
            if (validator.processOneFile()) {
                break;
            }
        }
    }

} // class SimpleCMDIValidatorProcessor
