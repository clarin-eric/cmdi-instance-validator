package eu.clarin.cmdi.validator;

import java.io.File;
import java.util.List;


public interface CMDIValidatorResult {
    public enum Severity {
        INFO {
            @Override
            public String getShortcut() {
                return "I";
            }
        },
        WARNING {
            @Override
            public String getShortcut() {
                return "W";
            }
        },
        ERROR {
            @Override
            public String getShortcut() {
                return "E";
            }
        };

        public abstract String getShortcut();
    } // enum Severity

    public interface Message {
        public Severity getSeverity();


        public int getLineNumber();


        public int getColumnNumber();


        public String getMessage();


        public Throwable getCause();
    } // interface Message


    public File getFile();


    public Severity getHighestSeverity();


    public boolean isHighestSeverity(Severity severity);


    public List<Message> getMessages();


    public Message getFirstMessage();


    public Message getFirstMessage(Severity severity);


    public int getMessageCount();


    public int getMessageCount(Severity severity);

} // CMDIValidatorResult
