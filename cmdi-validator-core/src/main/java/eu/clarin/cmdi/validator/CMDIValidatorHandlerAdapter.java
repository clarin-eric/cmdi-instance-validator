package eu.clarin.cmdi.validator;


public class CMDIValidatorHandlerAdapter implements CMDIValidatorHandler {

    @Override
    public void onJobStarted() throws CMDIValidatorException {
    }


    @Override
    public void onJobFinished(final CMDIValidator.Result result)
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
