package eu.clarin.cmdi.validator;

import java.io.File;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;


public final class CMDIValidatorResultImpl implements CMDIValidatorResult {
    private static class MessageImpl implements CMDIValidatorResult.Message {
        private Severity severity;
        private int line;
        private int col;
        private String message;
        private Throwable cause;


        private MessageImpl(Severity severity, int line, int col, String message,
                Throwable cause) {
            this.severity = severity;
            this.line = line;
            this.col = col;
            this.message = message;
        }


        @Override
        public Severity getSeverity() {
            return severity;
        }


        @Override
        public int getLineNumber() {
            return line;
        }


        @Override
        public int getColumnNumber() {
            return col;
        }


        @Override
        public String getMessage() {
            return message;
        }


        @Override
        public Throwable getCause() {
            return cause;
        }
    }
    private final List<Message> entries = new LinkedList<Message>();
    private File file;
    private Severity highestSeverity;


    CMDIValidatorResultImpl() {
        reset();
    }


    @Override
    public File getFile() {
        return file;
    }


    @Override
    public Severity getHighestSeverity() {
        return highestSeverity;
    }


    @Override
    public boolean isHighestSeverity(Severity severity) {
        if (severity == null) {
            throw new NullPointerException("severity == null");
        }
        return this.highestSeverity.equals(severity);
    }


    public boolean isEmpty() {
        return entries.isEmpty();
    }


    @Override
    public List<Message> getMessages() {
        if (entries.isEmpty()) {
            return Collections.emptyList();
        } else {
            return Collections.unmodifiableList(entries);
        }
    }


    @Override
    public Message getFirstMessage() {
        return entries.isEmpty() ? null : entries.get(0);
    }


    @Override
    public Message getFirstMessage(Severity severity) {
        if (severity == null) {
            throw new NullPointerException("severity == null");
        }

        if (!entries.isEmpty()) {
            for (Message msg : entries) {
                if (severity.equals(msg.getSeverity())) {
                    return msg;
                }
            }
        }
        return null;
    }


    @Override
    public int getMessageCount() {
        return entries.size();
    }


    @Override
    public int getMessageCount(Severity severity) {
        if (severity == null) {
            throw new NullPointerException("severity == null");
        }

        int count = 0;
        if (!entries.isEmpty()) {
            for (Message msg : entries) {
                if (severity.equals(msg.getSeverity())) {
                    count++;
                }
            }
        }
        return count;
    }


    public void reportInfo(int line, int col, String message) {
        reportInfo(line, col, message, null);
    }


    public void reportInfo(int line, int col, String message, Throwable cause) {
        entries.add(new MessageImpl(Severity.INFO, line, col, message, cause));
    }


    public void reportWarning(int line, int col, String message) {
        reportWarning(line, col, message, null);
    }


    public void reportWarning(int line, int col, String message, Throwable cause) {
        switch (highestSeverity) {
        case WARNING:
            /* FALL-THROUGH */
        case ERROR:
            break;
        default:
            highestSeverity = Severity.WARNING;
        }
        entries.add(new MessageImpl(Severity.WARNING, line, col, message, cause));
    }


    public void reportError(int line, int col, String message) {
        reportError(line, col, message, null);
    }


    public void reportError(int line, int col, String message, Throwable cause) {
        switch (highestSeverity) {
        case ERROR:
            break;
        default:
            highestSeverity = Severity.ERROR;
        }
        entries.add(new MessageImpl(Severity.ERROR, line, col, message, cause));
    }


    void setFile(File file) {
        this.file = file;
    }


    void reset() {
        this.entries.clear();
        this.file = null;
        this.highestSeverity = Severity.INFO;
    }

} // class CMDIValidatorMessages
