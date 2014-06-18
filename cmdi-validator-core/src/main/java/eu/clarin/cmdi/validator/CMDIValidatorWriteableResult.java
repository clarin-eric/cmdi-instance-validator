package eu.clarin.cmdi.validator;

public interface CMDIValidatorWriteableResult extends CMDIValidatorResult {
    public void reportInfo(int line, int col, String message);


    public void reportInfo(int line, int col, String message, Throwable cause);


    public void reportWarning(int line, int col, String message);


    public void reportWarning(int line, int col, String message, Throwable cause);


    public void reportError(int line, int col, String message);


    public void reportError(int line, int col, String message, Throwable cause);

} // interface CMDIValidatorWriteableResult
