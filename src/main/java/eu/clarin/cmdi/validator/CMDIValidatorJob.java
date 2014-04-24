package eu.clarin.cmdi.validator;

import java.util.LinkedList;
import java.util.List;

import net.java.truevfs.access.TFile;


public class CMDIValidatorJob {
    private final CMDIValidatorJobHandler handler;
    private final FileEnumerator fileEnumerator;
    private int filesInProcessing = 0;
    private boolean canceled = false;


    public CMDIValidatorJob(TFile root, CMDIValidatorJobHandler handler) {
        if (root == null) {
            throw new NullPointerException("root = null");
        }

        /*
         * XXX: maybe allow null handler or even a list of handlers?
         */
        if (handler == null) {
            throw new NullPointerException("handler == null");
        }
        this.handler = handler;
        this.fileEnumerator = new FileEnumerator(root);
        this.handler.onJobStarted();
    }


    public void cancel() {
        synchronized (this) {
            if (canceled) {
                throw new IllegalStateException("already canceled");
            }
            canceled = true;
            fileEnumerator.flush();
            try {
                this.wait();
            } catch (InterruptedException e) {
                /* IGNORE */
            }
        }
    }


    final CMDIValidatorJobHandler getHandler() {
        return handler;
    }


    final TFile getFileForProcessing(List<CMDIValidatorJob> jobList) {
        synchronized (this) {
            TFile file = null;
            if (!canceled) {
                file = fileEnumerator.nextFile();
                if (file != null) {
                    filesInProcessing++;
                }
            }

            if (fileEnumerator.isEmpty() || (file == null)) {
                jobList.remove(this);
            }
            return file;
        } // synchronized (this)
    }


    final boolean returnFileFromProcessing(TFile file) {
        synchronized (this) {
            filesInProcessing--;
            if (filesInProcessing < 0) {
                throw new IllegalStateException("filesInProcessing < 0");
            }
            if (fileEnumerator.isEmpty() && (filesInProcessing == 0)) {
                handler.onJobFinished(canceled);
                if (canceled) {
                    this.notifyAll();
                    return true;
                }
            }
        } // synchronized (this)
        return false;
    }


    private final static class FileEnumerator {
        private static final class FileList {
            private final TFile[] fileList;
            private int idx = 0;


            private FileList(TFile[] fileList) {
                this.fileList = fileList;
            }


            private TFile nextFile() {
                if (idx < fileList.length) {
                    return fileList[idx++];
                } else {
                    return null;
                }
            }

            private int size() {
                return (fileList.length - idx);
            }
        }
        private final LinkedList<FileList> stack =
                new LinkedList<FileList>();


        private FileEnumerator(TFile root) {
            if (root == null) {
                throw new NullPointerException("root == null");
            }
            if (!root.isDirectory()) {
                throw new IllegalArgumentException("root is not a directory");
            }
            pushDirectory(root);
        }


        private boolean isEmpty() {
            return stack.isEmpty();
        }


        private TFile nextFile() {
            for (;;) {
                if (stack.isEmpty()) {
                    break;
                }
                final FileList list = stack.peek();
                final TFile file = list.nextFile();
                if ((list.size() == 0) || (file == null)) {
                    stack.pop();
                    if (file == null) {
                        continue;
                    }
                }
                if (file.isDirectory()) {
                    pushDirectory(file);
                    continue;
                }
                return file;
            }
            return null;
        }

        private void flush() {
            stack.clear();
        }

        private void pushDirectory(TFile directory) {
            final TFile[] files = directory.listFiles();
            if ((files != null) && (files.length > 0)) {
                stack.push(new FileList(files));
            }
        }
    }

} // class CMDIValidatorJob
