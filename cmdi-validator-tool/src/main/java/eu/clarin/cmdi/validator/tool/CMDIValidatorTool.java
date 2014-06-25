/**
 * This software is copyright (c) 2014 by
 *  - Institut fuer Deutsche Sprache (http://www.ids-mannheim.de)
 * This is free software. You can redistribute it
 * and/or modify it under the terms described in
 * the GNU General Public License v3 of which you
 * should have received a copy. Otherwise you can download
 * it from
 *
 *   http://www.gnu.org/licenses/gpl-3.0.txt
 *
 * @copyright Institut fuer Deutsche Sprache (http://www.ids-mannheim.de)
 *
 * @license http://www.gnu.org/licenses/gpl-3.0.txt
 *  GNU General Public License v3
 */
package eu.clarin.cmdi.validator.tool;

import humanize.Humanize;

import java.io.File;
import java.io.PrintWriter;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import net.java.truevfs.access.TFile;
import net.java.truevfs.access.TVFS;
import net.java.truevfs.kernel.spec.FsSyncException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.clarin.cmdi.validator.ThreadedCMDIValidatorProcessor;
import eu.clarin.cmdi.validator.CMDIValidator;
import eu.clarin.cmdi.validator.CMDIValidatorConfig;
import eu.clarin.cmdi.validator.CMDIValidatorException;
import eu.clarin.cmdi.validator.CMDIValidatorInitException;
import eu.clarin.cmdi.validator.CMDIValidatorHandlerAdapter;
import eu.clarin.cmdi.validator.CMDIValidatorResult;
import eu.clarin.cmdi.validator.CMDIValidatorResult.Message;
import eu.clarin.cmdi.validator.CMDIValidatorResult.Severity;
import eu.clarin.cmdi.validator.extensions.CheckHandlesExtension;
import eu.clarin.cmdi.validator.utils.HandleResolver;


public class CMDIValidatorTool {
    private static final String PRG_NAME                 = "cmdi-validator";
    private static final long DEFAULT_PROGRESS_INTERVAL  = 15000;
    private static final Locale LOCALE                   = Locale.ENGLISH;
    private static final char OPT_DEBUG                  = 'd';
    private static final char OPT_QUIET                  = 'q';
    private static final char OPT_VERBOSE                = 'v';
    private static final char OPT_THREAD_COUNT           = 't';
    private static final char OPT_NO_THREADS             = 'T';
    private static final char OPT_NO_ESTIMATE            = 'E';
    private static final char OPT_SCHEMA_CACHE_DIR       = 'c';
    private static final char OPT_NO_SCHEMATRON          = 'S';
    private static final char OPT_SCHEMATRON_FILE        = 's';
    private static final char OPT_CHECK_PIDS             = 'p';
    private static final char OPT_CHECK_AND_RESOLVE_PIDS = 'P';
    private static final Logger logger =
            LoggerFactory.getLogger(CMDIValidatorTool.class);
    private static final org.apache.log4j.ConsoleAppender appender;


    public static void main(String[] args) {
        /*
         * application defaults
         */
        boolean debugging         = false;
        boolean quiet             = false;
        boolean verbose           = false;
        int threadCount           = Runtime.getRuntime().availableProcessors();
        boolean estimate          = true;
        long progressInterval     = DEFAULT_PROGRESS_INTERVAL;
        File schemaCacheDir       = null;
        boolean disableSchematron = false;
        File schematronFile       = null;
        boolean checkPids         = false;
        boolean checkAndResolvePids   = false;

        /*
         * setup command line parser
         */
        final Options options = createCommandLineOptions();
        try {
            final CommandLineParser parser = new GnuParser();
            final CommandLine line = parser.parse(options, args);
            // check incompatible combinations
            if (line.hasOption(OPT_THREAD_COUNT) && line.hasOption(OPT_NO_THREADS)) {
                throw new ParseException("The -t and -T options are mutually exclusive");
            }
            if (line.hasOption(OPT_DEBUG) && line.hasOption(OPT_QUIET)) {
                throw new ParseException("The -d and -q switches are mutually exclusive");
            }
            if (line.hasOption(OPT_VERBOSE) && line.hasOption(OPT_QUIET)) {
                throw new ParseException("The -v and -q switches are mutually exclusive");
            }
            if (line.hasOption(OPT_NO_SCHEMATRON) && line.hasOption(OPT_SCHEMATRON_FILE)) {
                throw new ParseException("The -s and -S options are mutually exclusive");
            }
            if (line.hasOption(OPT_CHECK_PIDS) && line.hasOption(OPT_CHECK_AND_RESOLVE_PIDS)) {
                throw new ParseException("The -p and -P options are mutually exclusive");
            }

            // extract options
            if (line.hasOption(OPT_DEBUG)) {
                debugging = true;
            }
            if (line.hasOption(OPT_QUIET)) {
                quiet = true;
            }
            if (line.hasOption(OPT_VERBOSE)) {
                verbose = true;
            }
            if (quiet) {
                progressInterval = -1;
            }
            if (line.hasOption(OPT_THREAD_COUNT)) {
                try {
                    threadCount = Integer.parseInt(
                            line.getOptionValue(OPT_THREAD_COUNT));
                    if (threadCount < 1) {
                        throw new ParseException(
                                "thread count must be larger then 0");
                    }
                } catch (NumberFormatException e) {
                    throw new ParseException("invalid number");
                }
            }
            if (line.hasOption(OPT_NO_THREADS)) {
                threadCount = 1;
            }
            if (line.hasOption(OPT_NO_ESTIMATE) || (progressInterval < 0)) {
                estimate = false;
            }
            if (line.hasOption(OPT_SCHEMA_CACHE_DIR)) {
                String dir = line.getOptionValue(OPT_SCHEMA_CACHE_DIR);
                if ((dir == null) || dir.isEmpty()) {
                    throw new ParseException("invalid argument for -" +
                            OPT_SCHEMA_CACHE_DIR);
                }
                schemaCacheDir = new File(dir);
            }
            if (line.hasOption(OPT_NO_SCHEMATRON)) {
                disableSchematron = true;
            }
            if (line.hasOption(OPT_SCHEMATRON_FILE)) {
                String name = line.getOptionValue(OPT_SCHEMATRON_FILE);
                if ((name == null) || name.isEmpty()) {
                    throw new ParseException("invalid argument for -" +
                            OPT_SCHEMATRON_FILE);
                }
                schematronFile = new File(name);
            }
            if (line.hasOption(OPT_CHECK_PIDS)) {
                checkPids = true;
            }
            if (line.hasOption(OPT_CHECK_AND_RESOLVE_PIDS)) {
                checkAndResolvePids = true;
            }

            final String[] remaining = line.getArgs();
            if ((remaining == null) || (remaining.length == 0)) {
                throw new ParseException("require <DIRECTORY> or <FILE> as " +
                        "additional command line parameter");
            }

            final org.apache.log4j.Logger log =
                    org.apache.log4j.Logger.getLogger(
                            CMDIValidator.class.getPackage().getName());
            if (debugging) {
                appender.setLayout(
                        new org.apache.log4j.PatternLayout("[%p] %t: %m%n"));
                log.setLevel(org.apache.log4j.Level.DEBUG);
            } else {
                if (quiet) {
                    log.setLevel(org.apache.log4j.Level.ERROR);
                } else {
                    log.setLevel(org.apache.log4j.Level.INFO);
                }
            }

            TFile archive = null;
            try {
                if (schemaCacheDir != null) {
                    logger.info("using schema cache directory: {}", schemaCacheDir);
                }
                if (schematronFile != null) {
                    logger.info("using Schematron schema from file: {}", schematronFile);
                }

                /*
                 * process archive
                 */
                archive = new TFile(remaining[0]);
                if (archive.exists()) {
                    if (archive.isArchive()) {
                        logger.info("reading archive '{}'", archive);
                    } else {
                        logger.info("reading directory '{}'", archive);
                    }

                    int totalFileCount = -1;
                    if (estimate && logger.isInfoEnabled()) {
                        logger.debug("counting files ...");
                        totalFileCount = countFiles(archive);
                    }

                    if (threadCount > 1) {
                      logger.debug("using {} threads", threadCount);
                    }


                    final ValidationHandler handler = new ValidationHandler(verbose);

                    final CMDIValidatorConfig.Builder builder =
                            new CMDIValidatorConfig.Builder(archive, handler);
                    if (schemaCacheDir != null) {
                        builder.schemaCacheDirectory(schemaCacheDir);
                    }
                    if (schematronFile != null) {
                        builder.schematronSchemaFile(schematronFile);
                    }
                    if (disableSchematron) {
                        builder.disableSchematron();
                    }

                    CheckHandlesExtension checkHandleExtension = null;
                    if (checkPids || checkAndResolvePids) {
                        if (checkAndResolvePids) {
                            logger.info("enabling PID validation (syntax and resolving)");
                        } else {
                            logger.info("enabling PID validation (syntax only)");
                        }
                        checkHandleExtension =
                                new CheckHandlesExtension(checkAndResolvePids);
                        builder.extension(checkHandleExtension);
                    }

                    final ThreadedCMDIValidatorProcessor processor =
                            new ThreadedCMDIValidatorProcessor(threadCount);
                    processor.start();
                    try {
                        final CMDIValidator validator =
                                new CMDIValidator(builder.build());
                        processor.process(validator);

                        /*
                         * Wait until validation is done and report about
                         * progress every now and then ...
                         */
                        for (;;) {
                            try {
                                if (handler.await(progressInterval)) {
                                    break;
                                }
                            } catch (InterruptedException e) {
                                /* IGNORE */
                            }
                            if ((progressInterval > 0) && logger.isInfoEnabled()) {
                                final long now = System.currentTimeMillis();
                                int fps    = -1;
                                long bps   = -1;
                                int count  = 0;
                                long delta = -1;
                                synchronized (handler) {
                                    delta = (now - handler.getTimeStarted()) / 1000;
                                    if (delta > 0) {
                                        fps = (int) (handler.getTotalFileCount() / delta);
                                        bps = (handler.getTotalBytes() / delta);
                                    }
                                    count = handler.getTotalFileCount();
                                } // synchronized (result)
                                if (totalFileCount > 0) {
                                    float complete = (count / (float)  totalFileCount) * 100f;
                                    logger.info("processed {} files ({}%) in {} ({} files/second, {}/second) ...",
                                            count,
                                            String.format(LOCALE, "%.2f", complete),
                                            Humanize.duration(delta, LOCALE),
                                            ((fps != -1) ? fps : "N/A"),
                                            ((bps != -1) ? Humanize.binaryPrefix(bps, LOCALE) : "N/A MB"));
                                } else {
                                    logger.info("processed {} files in {} ({} files/second, {}/second) ...",
                                            count,
                                            Humanize.duration(delta, LOCALE),
                                            ((fps != -1) ? fps : "N/A"),
                                            ((bps != -1) ? Humanize.binaryPrefix(bps, LOCALE) : "N/A MB"));
                                }
                                if (logger.isDebugEnabled()) {
                                    if ((checkHandleExtension != null) &&
                                            checkHandleExtension.isResolvingHandles()) {
                                        final HandleResolver.Statistics stats =
                                                checkHandleExtension.getStatistics();
                                        logger.debug("[handle resolver stats] total requests: {}, running requests: {}, cache hits: {}, cache misses: {}, current cache size: {}",
                                                stats.getTotalRequestsCount(),
                                                stats.getCurrentRequestsCount(),
                                                stats.getCacheHitCount(),
                                                stats.getCacheMissCount(),
                                                stats.getCurrentCacheSize());
                                    }
                                }
                            }
                        } // for (;;)
                    } finally {
                        processor.shutdown();
                    }

                    int fps = -1;
                    long bps = -1;
                    if (handler.getTimeElapsed() > 0) {
                        fps = (int) (handler.getTotalFileCount() / handler.getTimeElapsed());
                        bps = handler.getTotalBytes() / handler.getTimeElapsed();
                    }

                    logger.info("time elapsed: {}, validation result: {}% failure rate (files: {} total, {} passed, {} failed; {} total, {} files/second, {}/second)",
                            Humanize.duration(handler.getTimeElapsed(), LOCALE),
                            String.format(LOCALE, "%.2f", handler.getFailureRate() * 100f),
                            handler.getTotalFileCount(),
                            handler.getValidFileCount(),
                            handler.getInvalidFileCount(),
                            Humanize.binaryPrefix(handler.getTotalBytes(), LOCALE),
                            ((fps != -1) ? fps : "N/A"),
                            ((bps != -1) ? Humanize.binaryPrefix(bps, LOCALE) : "N/A MB"));
                    logger.debug("... done");
                } else {
                    logger.error("not found: {}", archive);
                }
            } finally {
                if (archive != null) {
                    try {
                        TVFS.umount(archive);
                    } catch (FsSyncException e) {
                        logger.error("error unmounting archive", e);
                    }
                }
            }
        } catch (CMDIValidatorException e) {
            logger.error("error initalizing job: {}", e.getMessage());
            if (debugging) {
                logger.error(e.getMessage(), e);
            }
            System.exit(1);
        } catch (CMDIValidatorInitException e) {
            logger.error("error initializing validator: {}", e.getMessage());
            if (debugging) {
                logger.error(e.getMessage(), e);
            }
            System.exit(2);
        } catch (ParseException e) {
            PrintWriter writer = new PrintWriter(System.err);
            if (e.getMessage() != null) {
                writer.print("ERROR: ");
                writer.println(e.getMessage());
            }
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp(writer, HelpFormatter.DEFAULT_WIDTH, PRG_NAME,
                    null, options, HelpFormatter.DEFAULT_LEFT_PAD,
                    HelpFormatter.DEFAULT_DESC_PAD, null, true);
            writer.flush();
            writer.close();
            System.exit(64); /* EX_USAGE */
        }
    }


    @SuppressWarnings("static-access")
    private static Options createCommandLineOptions() {
        final Options options = new Options();
        OptionGroup g1 = new OptionGroup();
        g1.addOption(OptionBuilder
                .withDescription("enable debugging output")
                .withLongOpt("debug")
                .create(OPT_DEBUG));
        g1.addOption(OptionBuilder
                .withDescription("be quiet")
                .withLongOpt("quiet")
                .create(OPT_QUIET));
        options.addOptionGroup(g1);
        options.addOption(OptionBuilder
                .withDescription("be verbose")
                .withLongOpt("verbose")
                .create(OPT_VERBOSE));
        OptionGroup g2 = new OptionGroup();
        g2.addOption(OptionBuilder
                .withDescription("number of validator threads")
                .hasArg()
                .withArgName("COUNT")
                .withLongOpt("threads")
                .create(OPT_THREAD_COUNT));
        g2.addOption(OptionBuilder
                .withDescription("disable threading")
                .withLongOpt("no-threads")
                .create(OPT_NO_THREADS));
        options.addOptionGroup(g2);
        options.addOption(OptionBuilder
            .withDescription("disable gathering of total file count for progress reporting")
            .withLongOpt("no-estimate")
            .create(OPT_NO_ESTIMATE));
        options.addOption(OptionBuilder
                .withDescription("schema caching directory")
                .hasArg()
                .withArgName("DIRECTORY")
                .withLongOpt("schema-cache-dir")
                .create(OPT_SCHEMA_CACHE_DIR));
        OptionGroup g3 = new OptionGroup();
        g3.addOption(OptionBuilder
                .withDescription("disable Schematron validator")
                .withLongOpt("no-schematron")
                .create(OPT_NO_SCHEMATRON));
        g3.addOption(OptionBuilder
                .withDescription("load Schematron schema from file")
                .hasArg()
                .withArgName("FILE")
                .withLongOpt("schematron-file")
                .create(OPT_SCHEMATRON_FILE));
        options.addOptionGroup(g3);
        OptionGroup g4 = new OptionGroup();
        g4.addOption(OptionBuilder
                .withDescription("check persistent identifiers syntax")
                .withLongOpt("check-pids")
                .create(OPT_CHECK_PIDS));
        g4.addOption(OptionBuilder
                .withDescription("check persistent identifiers syntax and if they resolve properly")
                .withLongOpt("check-and-resolve-pids")
                .create(OPT_CHECK_AND_RESOLVE_PIDS));
        options.addOptionGroup(g4);
        return options;
    }


    private static final int countFiles(TFile directory) {
        int count = 0;
        final TFile[] entries = directory.listFiles();
        if ((entries != null) && (entries.length > 0)) {
            for (TFile entry : entries) {
                if (entry.isDirectory()) {
                    count += countFiles(entry);
                } else {
                    count++;
                }
            }
        }
        return count;
    }


    private static class ValidationHandler extends CMDIValidatorHandlerAdapter {
        private final boolean verbose;
        private long started     = System.currentTimeMillis();
        private long finished    = -1;
        private int filesTotal   = 0;
        private int filesInvalid = 0;
        private long totalBytes  = 0;
        private boolean isCompleted = false;
        private final Object waiter = new Object();


        private ValidationHandler(boolean verbose) {
            this.verbose = verbose;
        }


        public long getTimeStarted() {
            return started;
        }


        public long getTimeElapsed() {
            long duration = (finished != -1)
                    ? (finished - started)
                    : (System.currentTimeMillis() - started);
            return TimeUnit.MILLISECONDS.toSeconds(duration);
        }


        public int getTotalFileCount() {
            return filesTotal;
        }


        public int getValidFileCount() {
            return filesTotal - filesInvalid;
        }

        public int getInvalidFileCount() {
            return filesInvalid;
        }


        public float getFailureRate() {
            return (filesTotal > 0)
                    ? ((float) filesInvalid / (float) filesTotal)
                    : 0.0f;
        }


        public long getTotalBytes() {
            return totalBytes;
        }


        public boolean await(long timeout) throws InterruptedException {
            synchronized (waiter) {
                if (isCompleted) {
                    return true;
                }
                if (timeout > 0) {
                    waiter.wait(timeout);
                } else {
                    waiter.wait();
                }
                return isCompleted;
            }
        }


        @Override
        public void onJobStarted() throws CMDIValidatorException {
            logger.debug("validation process started");
        }


        @Override
        public void onJobFinished(final CMDIValidator.Result result)
                throws CMDIValidatorException {
            finished = System.currentTimeMillis();
            switch (result) {
            case OK:
                logger.debug("validation process finished successfully");
                break;
            case ABORTED:
                logger.info("processing was aborted");
                break;
            case ERROR:
                logger.debug("validation process yielded an error");
                break;
            default:
                logger.debug("unknown result: " + result);
            } // switch

            synchronized (waiter) {
                isCompleted = true;
                waiter.notifyAll();
            } // synchronized (waiter)
        }


        @Override
        public void onValidationSuccess(final CMDIValidatorResult result)
                throws CMDIValidatorException {
            final File file = result.getFile();
            synchronized (this) {
                filesTotal++;
                if (file != null) {
                    totalBytes += file.length();
                }
            } // synchronized (this) {

            if (result.isHighestSeverity(Severity.WARNING)) {
                if (verbose) {
                    logger.warn("file '{}' is valid (with warnings):", file);
                    for (Message msg : result.getMessages()) {
                        if ((msg.getLineNumber() != -1) &&
                                (msg.getColumnNumber() != -1)) {
                            logger.warn(" ({}) {} [line={}, column={}]",
                                    msg.getSeverity().getShortcut(),
                                    msg.getMessage(),
                                    msg.getLineNumber(),
                                    msg.getColumnNumber());
                        } else {
                            logger.warn(" ({}) {}",
                                    msg.getSeverity().getShortcut(),
                                    msg.getMessage());
                        }
                    }
                } else {
                    Message msg = result.getFirstMessage(Severity.WARNING);
                    int count   = result.getMessageCount(Severity.WARNING);
                    if (count > 1) {
                        logger.warn("file '{}' is valid (with warnings): {} ({} more warnings)",
                                file, msg.getMessage(), (count - 1));
                    } else {
                        logger.warn("file '{}' is valid (with warnings): {}",
                                file, msg.getMessage());
                    }
                }
            } else {
                logger.debug("file '{}' is valid", file);
            }
        }


        @Override
        public void onValidationFailure(final CMDIValidatorResult result) {
            final File file = result.getFile();
            synchronized (this) {
                filesTotal++;
                filesInvalid++;
                if (file != null) {
                    totalBytes += file.length();
                }
            } // synchronized (this)

            if (verbose) {
            logger.error("file '{}' is invalid:", file);
                for (Message msg : result.getMessages()) {
                    if ((msg.getLineNumber() != -1) &&
                            (msg.getColumnNumber() != -1)) {
                        logger.error(" ({}) {} [line={}, column={}]",
                                msg.getSeverity().getShortcut(),
                                msg.getMessage(),
                                msg.getLineNumber(),
                                msg.getColumnNumber());
                    } else {
                        logger.error(" ({}) {}",
                                msg.getSeverity().getShortcut(),
                                msg.getMessage());
                    }
                }
            } else {
                Message msg = result.getFirstMessage(Severity.ERROR);
                int count   = result.getMessageCount(Severity.ERROR);
                if (count > 1) {
                    logger.error("file '{}' is invalid: {} ({} more errors)",
                            file, msg.getMessage(), (count - 1));
                } else {
                    logger.error("file '{}' is invalid: {}",
                            file, msg.getMessage());
                }
            }
        }
    } // class JobHandler


    static {
        appender = new org.apache.log4j.ConsoleAppender(
                new org.apache.log4j.PatternLayout("%m%n"),
                org.apache.log4j.ConsoleAppender.SYSTEM_OUT);
        org.apache.log4j.BasicConfigurator.configure(appender);
        org.apache.log4j.Logger logger =
                org.apache.log4j.Logger.getRootLogger();
        logger.setLevel(org.apache.log4j.Level.WARN);
    }

} // class CMDIValidatorTool
