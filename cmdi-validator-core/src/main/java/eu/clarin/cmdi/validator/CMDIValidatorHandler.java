package eu.clarin.cmdi.validator;


public interface CMDIValidatorHandler {

    public void onJobStarted()
            throws CMDIValidatorException;


    public void onJobFinished(final CMDIValidator.Result result)
            throws CMDIValidatorException;


    public void onValidationSuccess(final CMDIValidatorResult result)
            throws CMDIValidatorException;


    public void onValidationFailure(final CMDIValidatorResult result)
            throws CMDIValidatorException;

} // interface CMDIValidatorJobHandler
