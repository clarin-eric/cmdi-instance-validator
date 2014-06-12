package eu.clarin.cmdi.validator;


public class CMDIValidatorJobHandlerAdapter implements CMDIValidatorJobHandler {

    @Override
    public void onJobStarted() throws CMDIValidatorException {
    }


    @Override
    public void onJobFinished(boolean wasCanceled)
            throws CMDIValidatorException {
    }


    @Override
    public void onValidationSuccess(CMDIValidatorResult result)
            throws CMDIValidatorException {
    }


    @Override
    public void onValidationFailure(CMDIValidatorResult result)
            throws CMDIValidatorException {
    }

} // class CMDIValidatorJobHandlerAdapter
