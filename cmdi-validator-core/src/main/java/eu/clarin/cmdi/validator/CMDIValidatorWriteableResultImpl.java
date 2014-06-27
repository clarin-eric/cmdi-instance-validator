package eu.clarin.cmdi.validator;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class CMDIValidatorWriteableResultImpl implements CMDIValidatorWriteableResult {
    private final File file;
    private List<Message> messages;
    private Severity highestSeverity = Severity.INFO;


    CMDIValidatorWriteableResultImpl(File file) {
        this.file = file;
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
        return highestSeverity.equals(severity);
    }


    @Override
    public List<Message> getMessages() {
        if (messages == null) {
            return Collections.emptyList();
        } else {
            return Collections.unmodifiableList(messages);
        }
    }


    @Override
    public Message getFirstMessage() {
        return (messages != null) ? null : messages.get(0);
    }


    @Override
    public Message getFirstMessage(Severity severity) {
        if (severity == null) {
            throw new NullPointerException("severity == null");
        }

        if (messages != null) {
            for (Message msg : messages) {
                if (severity.equals(msg.getSeverity())) {
                    return msg;
                }
            }
        }
        return null;
    }


    @Override
    public int getMessageCount() {
        return (messages != null) ? messages.size() : 0;
    }


    @Override
    public int getMessageCount(Severity severity) {
        if (severity == null) {
            throw new NullPointerException("severity == null");
        }

        int count = 0;
        if (messages != null) {
            for (Message msg : messages) {
                if (severity.equals(msg.getSeverity())) {
                    count++;
                }
            }
        }
        return count;
    }


    @Override
    public void reportInfo(int line, int col, String message) {
        reportInfo(line, col, message, null);
    }


    @Override
    public void reportInfo(int line, int col, String message,
            Throwable cause) {
        addMessage(Severity.INFO, line, col, message, cause);
    }


    @Override
    public void reportWarning(int line, int col, String message) {
        reportWarning(line, col, message, null);
    }


    @Override
    public void reportWarning(int line, int col, String message,
            Throwable cause) {
        addMessage(Severity.WARNING, line, col, message, cause);
    }


    @Override
    public void reportError(int line, int col, String message) {
        reportError(line, col, message, null);
    }


    @Override
    public void reportError(int line, int col, String message,
            Throwable cause) {
        addMessage(Severity.ERROR, line, col, message, cause);
    }


    private void addMessage(final Severity severity,
            final int line,
            final int col,
            final String message,
            final Throwable cause) {
        if (messages == null) {
            messages = new ArrayList<Message>(8);
        }
        if (severity.priority() > highestSeverity.priority()) {
            highestSeverity = severity;
        }
        messages.add(new MessageImpl(severity, line, col, message, cause));
    }


    private static final class MessageImpl implements
            CMDIValidatorResult.Message {
        private final Severity severity;
        private final int line;
        private final int col;
        private final String message;
        private final Throwable cause;


        private MessageImpl(Severity severity, int line, int col,
                String message, Throwable cause) {
            this.severity = severity;
            this.line = line;
            this.col = col;
            this.message = message;
            this.cause = cause;
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
    } // class MessageImpl

} // class CMDIValidatorWriteableResultImpl
