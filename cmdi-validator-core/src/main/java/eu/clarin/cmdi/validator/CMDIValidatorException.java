package eu.clarin.cmdi.validator;

@SuppressWarnings("serial")
public class CMDIValidatorException extends Exception {
    public CMDIValidatorException(String message, Throwable cause) {
        super(message, cause);
    }


    public CMDIValidatorException(String message) {
        super(message);
    }
} // class CMDIValidatorException
