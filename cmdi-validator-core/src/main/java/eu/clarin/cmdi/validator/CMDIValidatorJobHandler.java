package eu.clarin.cmdi.validator;


public interface CMDIValidatorJobHandler {

    public void onJobStarted()
            throws CMDIValidatorException;


    public void onJobFinished(boolean wasCanceled)
            throws CMDIValidatorException;


    public void onValidationSuccess(CMDIValidatorResult result)
            throws CMDIValidatorException;


    public void onValidationFailure(CMDIValidatorResult result)
            throws CMDIValidatorException;

} // interface CMDIValidatorJobHandler
