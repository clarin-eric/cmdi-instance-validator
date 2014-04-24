package eu.clarin.cmdi.validator;


public interface CMDIValidatorJobHandler {
    public void onJobStarted();


    public void onJobFinished(boolean wasCanceled);


    public void onValidationSuccess(CMDIValidatorResult result);


    public void onValidationFailure(CMDIValidatorResult result);

} // interface CMDIValidatorJobHandler
