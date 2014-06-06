package eu.clarin.cmdi.validator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.java.truevfs.access.TFile;
import net.java.truevfs.access.TFileInputStream;


public class CMDIValidatorEngine {
    private static final Logger logger =
            LoggerFactory.getLogger(CMDIValidatorEngine.class);
    private final CMDIValidatorFactory validatorFactory;
    private final int threadCount;
    private boolean running = false;
    private ThreadGroup workers;
    private ExecutorService executor;
    private JobList jobList;


    public CMDIValidatorEngine(CMDIValidatorFactory validatorFactory,
            int threadCount) {
        if (validatorFactory == null) {
            throw new NullPointerException("validatorFactory == null");
        }
        this.validatorFactory = validatorFactory;
        this.threadCount      = threadCount;
    }


    public CMDIValidatorEngine(CMDIValidatorFactory validatorFactory) {
        this(validatorFactory, Runtime.getRuntime().availableProcessors());
    }


    public synchronized void start() throws CMDIValidatorInitException {
        if (running) {
            throw new IllegalStateException("engine already started");
        }
        logger.debug("start validation engine");

        jobList = new JobList();

        workers = new ThreadGroup("Validation-Workers");
        final ThreadFactory threadFactory = new ThreadFactory() {
            private int id = 0;
            @Override
            public Thread newThread(Runnable target) {
                final String name = String.format("worker-%02x", id++);
                return new Thread(workers, target, name);
            }
        };
        final CountDownLatch goLatch = new CountDownLatch(1);
        executor = Executors.newFixedThreadPool(threadCount, threadFactory);
        for (int i = 0; i < threadCount; i++) {
            final CMDIValidator validator = validatorFactory.newValidator();
            final Worker worker = new Worker(jobList, validator, goLatch);
            executor.submit(worker);
        }
        goLatch.countDown();
        running = true;
    }


    public synchronized void shutdown() {
        logger.debug("shutdown validation engine");
        executor.shutdownNow();
        try {
            executor.awaitTermination(5000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
        }
    }


    public void submit(CMDIValidatorJob job) {
        if (job == null) {
            throw new NullPointerException("job == null");
        }
        if (!running) {
            throw new IllegalStateException("engine not started");
        }
        jobList.put(job);
    }


    private static void validateFile(final CMDIValidator validator,
            final CMDIValidatorJob job, final TFile file) {
        try {
            TFileInputStream stream = new TFileInputStream(file);
            try {
                logger.debug("validating file '{}' ({} bytes)",
                        file, file.length());
                validator.validate(stream, file, job.getHandler());
            } catch (CMDIValidatorException e) {
                logger.debug("validation of file '{}' failed", file, e);
            } finally {
                stream.close();
            }
        } catch (IOException e) {
            logger.error("error reading file '{}': {}",
                    file, e.getMessage());
        }
    }


    private static final class JobList {
        private static final class Holder {
            private CMDIValidatorJob job;
            private TFile file;

            private void clear() {
                job  = null;
                file = null;
            }
        }
        private final List<CMDIValidatorJob> jobs =
                    new ArrayList<CMDIValidatorJob>();
        private int idx = 0;


        private void put(CMDIValidatorJob job) {
            synchronized (jobs) {
                jobs.add(job);

                /* let the waiting herd stampede ... */
                jobs.notifyAll();
            } // synchronized (jobs)
        }


        private void get(JobList.Holder holder)
                throws InterruptedException {
            holder.clear();

            for (;;) {
                synchronized (jobs) {
                    if (!jobs.isEmpty()) {
                        holder.job  = jobs.get(idx++);
                        holder.file = holder.job.getFileForProcessing(jobs);
                        if (idx >= jobs.size()) {
                            idx = 0;
                        }
                        if (holder.file != null) {
                            break;
                        } else {
                            holder.clear();
                        }
                    }

                    /* no cookie for me, wait for someone to fill the jar ... */
                    jobs.wait();
                } // synchronized (jobs)
            } // for
        }


        private void remove(CMDIValidatorJob job) {
            synchronized (jobs) {
                jobs.remove(job);
                if (idx >= jobs.size()) {
                    idx = 0;
                }
            } // synchronized (jobs)
        }
    } // inner class JobList


    private static final class Worker implements Runnable {
        private final CountDownLatch goLatch;
        private final JobList jobList;
        private final CMDIValidator validator;


        private Worker(JobList jobList, CMDIValidator validator,
                CountDownLatch goLatch) {
            this.jobList   = jobList;
            this.validator = validator;
            this.goLatch   = goLatch;
        }


        @Override
        public void run() {
            try {
                final JobList.Holder holder = new JobList.Holder();

                // wait for start signal
                goLatch.await();

                // loop for work ...
                for (;;) {
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }
                    jobList.get(holder);

                    final CMDIValidatorJob job = holder.job;
                    final TFile file           = holder.file;
                    try {
                        validateFile(validator, job, file);
                    } finally {
                        if (job.returnFileFromProcessing(file)) {
                            jobList.remove(job);
                        }
                    }
                }
            } catch (InterruptedException e) {
                /* IGNORE */
            } catch (Throwable e) {
                logger.error("unexpected error in worker thread: {}",
                        e.getMessage(), e);
            }
        }
    }

} // class CMDIValidatorExecutor
