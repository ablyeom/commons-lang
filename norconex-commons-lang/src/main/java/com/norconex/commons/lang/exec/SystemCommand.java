/* Copyright 2010-2018 Norconex Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.norconex.commons.lang.exec;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.StringTokenizer;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.commons.lang.io.IInputStreamListener;
import com.norconex.commons.lang.io.InputStreamLineListener;

/**
 * Represents a program to be executed by the underlying system
 * (on the "command line").  This class attempts to be system-independent,
 * which means given an executable path should be sufficient to run
 * programs on any systems (e.g. it handles prefixing an executable with OS
 * specific commands as well as preventing process hanging on some OS when
 * there is nowhere to display the output).  
 * 
 * @author Pascal Essiembre
 * @since 1.13.0 (previously part of 
 *        <a href="https://www.norconex.com/jef/api/">JEF API</a> 4.0).
 */
public class SystemCommand {

    private static final Logger LOG = 
            LoggerFactory.getLogger(SystemCommand.class);

    private static final String[] CMD_PREFIXES_WIN_LEGACY = 
            new String[] { "command.com", "/C" };
    private static final String[] CMD_PREFIXES_WIN_CURRENT = 
            new String[] { "cmd.exe", "/C" };
    
    private static final IInputStreamListener[] EMPTY_LISTENERS =
    		new IInputStreamListener[] {};
    
    private final String[] command;
    private final File workdir;
    // Null means inherit from those of java process
    private Map<String, String> environmentVariables = null;

    private final List<IInputStreamListener> errorListeners =
            Collections.synchronizedList(new ArrayList<IInputStreamListener>());
    private final List<IInputStreamListener> outputListeners =
            Collections.synchronizedList(new ArrayList<IInputStreamListener>());

    private Process process;
    
    /**
     * Creates a command for which the execution will be in the working
     * directory of the current process.  If more than one command values
     * are passed, the first element of the array
     * is the command and subsequent elements are arguments.
     * If your command or arguments contain spaces, they will be escaped
     * according to your operating sytem (surrounding with double-quotes on 
     * Windows and backslash on other operating systems).
     * @param command the command to run
     */
    public SystemCommand(String... command) {
    	this(null, command);
    }
    
    /**
     * Creates a command. If more than one command values
     * are passed, the first element of the array
     * is the command and subsequent elements are arguments.
     * If your command or arguments contain spaces, they will be escaped
     * according to your operating sytem (surrounding with double-quotes on 
     * Windows and backslash on other operating systems).
     * @param command the command to run
     * @param workdir command working directory.
     */
    public SystemCommand(File workdir, String... command) {
        super();
        this.command = command;
        this.workdir = workdir;
    }
    
    
    /**
     * Gets the command to be run.
     * @return the command
     */
    public String[] getCommand() {
        return ArrayUtils.clone(command);
    }

    /**
     * Gets the command working directory.
     * @return command working directory.
     */
    public File getWorkdir() {
    	return workdir;
    }

    /**
     * Adds an error (STDERR) listener to this system command.
     * @param listener command error listener
     */
    public void addErrorListener(
            final IInputStreamListener listener) {
        synchronized (errorListeners) {
        	errorListeners.add(0, listener);
        }
    }
    /**
     * Removes an error (STDERR) listener.
     * @param listener command error listener
     */
    public void removeErrorListener(
            final IInputStreamListener listener) {
        synchronized (errorListeners) {
        	errorListeners.remove(listener);
        }
    }
    /**
     * Adds an output (STDOUT) listener to this system command.
     * @param listener command output listener
     */
    public void addOutputListener(
            final IInputStreamListener listener) {
        synchronized (outputListeners) {
        	outputListeners.add(0, listener);
        }
    }
    /**
     * Removes an output (STDOUT) listener.
     * @param listener command output listener
     */
    public void removeOutputListener(
            final IInputStreamListener listener) {
        synchronized (outputListeners) {
        	outputListeners.remove(listener);
        }
    }
    /**
     * Gets environment variables.
     * @return environment variables
     */
    public Map<String, String> getEnvironmentVariables() {
        return environmentVariables;
    }
    /**
     * Sets environment variables. Set to <code>null</code> (default) for the 
     * command to inherit the environment of the current process.
     * @param environmentVariables environment variables
     */
    public void setEnvironmentVariables(
            Map<String, String> environmentVariables) {
        this.environmentVariables = environmentVariables;
    }

    /**
     * Returns whether the command is currently running.
     * @return <code>true</code> if running
     */
    public boolean isRunning() {
    	if (process == null) {
    		return false;
    	}
    	try {
        	process.exitValue();
        	return false;
    	} catch (IllegalThreadStateException e) {
    		return true;
    	}
    }

    /**
     * Aborts the running command.  If the command is not currently running,
     * aborting it will have no effect.
     */
    public void abort() {
        if (process != null) {
            process.destroy();
        }
    }

    /**
     * Executes this system command and returns only when the underlying 
     * process stopped running.  
     * @return process exit value
     * @throws SystemCommandException problem executing command
     */
    public int execute() throws SystemCommandException {
        return execute(false);
    }

    /**
     * Executes this system command.  When run in the background,
     * this method does not wait for the process to complete before returning.
     * In such case the status code should always be 0 unless it terminated 
     * abruptly (may not reflect the process termination status).
     * When NOT run in the background, this method waits and returns 
     * only when the underlying process stopped running.  
     * Alternatively, to run a command asynchronously, you can wrap it in 
     * its own thread.
     * @param runInBackground <code>true</code> to runs the system command in 
     *         background.
     * @return process exit value
     * @throws SystemCommandException problem executing command
     * @throws IllegalStateException when command is already running
     */
    public int execute(boolean runInBackground) throws SystemCommandException {
        return execute(null, runInBackground);
    }

    /**
     * Executes this system command with the given input and returns only when 
     * the underlying process stopped running.
     * @param input process input (fed to STDIN)  
     * @return process exit value
     * @throws SystemCommandException problem executing command
     */
    public int execute(InputStream input) throws SystemCommandException {
        return execute(input, false);
    }
    
    /**
     * Executes this system command with the given input. When run in the 
     * background, this method does not wait for the process to complete before
     * returning.
     * In such case the status code should always be 0 unless it terminated 
     * abruptly (may not reflect the process termination status).
     * When NOT run in the background, this method waits and returns 
     * only when the underlying process stopped running.  
     * Alternatively, to run a command asynchronously, you can wrap it in 
     * its own thread.
     * @param input process input (fed to STDIN)  
     * @param runInBackground <code>true</code> to runs the system command in 
     *         background.
     * @return process exit value
     * @throws SystemCommandException problem executing command
     * @throws IllegalStateException when command is already running
     */
    public int execute(final InputStream input, boolean runInBackground) 
            throws SystemCommandException {
        if (isRunning()) {
            throw new IllegalStateException(
                    "Command is already running: " + toString());
        }
        String[] cleanCommand = getCleanCommand();
        if (LOG.isDebugEnabled()) {
            LOG.debug("Executing command: "
                    + StringUtils.join(cleanCommand, " "));
        }
        try {
            process = Runtime.getRuntime().exec(
                    cleanCommand, environmentArray(), workdir);
        } catch (IOException e) {
            throw new SystemCommandException("Could not execute command: "
                    + toString(), e);
        }
        
        IInputStreamListener[] outListeners = 
                outputListeners.toArray(EMPTY_LISTENERS);
        
        IInputStreamListener[] errListeners = 
                errorListeners.toArray(EMPTY_LISTENERS);
        ErrorTracker errorTracker = new ErrorTracker();
        errListeners = ArrayUtils.add(errListeners, errorTracker);
        
        int exitValue = 0;
        if (runInBackground) {
            ExecUtil.watchProcessAsync(
                    process, input, outListeners, errListeners);
            try {
                // Check in case the process terminated abruptly.
                exitValue = process.exitValue();
            } catch (IllegalThreadStateException e) {
                // Do nothing
            }
        } else {
            exitValue = ExecUtil.watchProcess(
                    process, input, outListeners, errListeners);
        }
        
        if (exitValue != 0) {
            LOG.error("Command returned with exit value " + process.exitValue()
                    + " (command properly escaped?). Command: "
                    + StringUtils.join(cleanCommand, " ") + " Error: \""
                    + errorTracker.b.toString() + "\"");
        }
        process = null;
        return exitValue;
    }

    /**
     * Returns the command to be executed.
     */
    @Override
    public String toString() {
        return StringUtils.join(command, " ");
    }

    private String[] environmentArray() {
        if (environmentVariables == null) {
            return null;
        }
        List<String> envs = new ArrayList<>();
        for (Entry<String, String> entry : environmentVariables.entrySet()) {
            envs.add(entry.getKey() + "=" + entry.getValue());
        }
        return envs.toArray(ArrayUtils.EMPTY_STRING_ARRAY);
    }
    
    private String[] getCleanCommand() throws SystemCommandException {
        List<String> cmd = 
                new ArrayList<>(Arrays.asList(ArrayUtils.nullToEmpty(command)));
        if (cmd.isEmpty()) {
            throw new SystemCommandException("No command specified.");
        }

        escape(cmd);
        wrapCommand(cmd);
        return cmd.toArray(ArrayUtils.EMPTY_STRING_ARRAY);
    }
    
    // Removes OS prefixes from a command since they will be re-added
    // and can affect processing logic if not
    private void removePrefixes(List<String> cmd, String[] prefixes) {
        if (ArrayUtils.isEmpty(prefixes) || cmd.isEmpty()) {
            return;
        }
        for (int i = 0; i < prefixes.length; i++) {
            String prefix = prefixes[i];
            if (cmd.size() > i && prefix.equalsIgnoreCase(cmd.get(0))) {
                cmd.remove(0);
            } else {
                return;
            }
        }
    }
    
    /**
     * Escapes spaces in each parts of the command as well as special
     * characters in some operating systems, if they are not already
     * escaped. Escapes according to operating system escape mechanism.
     * If there is only one part to the command (size-one list), it
     * can be the entire command with arguments passed as one string. In such
     * case, there are little ways to tell if spaces are part of an argument
     * or separates two arguments, so no escaping is performed.
     * @param command the command to escape.
     */
    public static void escape(List<String> command) {
//        if (SystemUtils.IS_OS_WINDOWS) {
            escapeWindows(command);
//        } else {
//            escapeNonWindows(command);
//        }
    }
    
    /**
     * Escapes spaces in each parts of the command as well as special
     * characters in some operating systems, if they are not already
     * escaped. Escapes according to operating system escape mechanism.
     * If there is only one part to the command (size-one array), it
     * can be the entire command with arguments passed as one string. In such
     * case, there are little ways to tell if spaces are part of an argument
     * or separates two arguments, so no escaping is performed.
     * @param command the command to escape.
     * @return escaped command
     */
    public static String[] escape(String... command) {
        List<String> list = new ArrayList<>(Arrays.asList(command));
        escape(list);
        return list.toArray(ArrayUtils.EMPTY_STRING_ARRAY);
    }
    
    
    // With windows, using cmd.exe /C requires putting arguments with 
    // spaces in quotes, and then everything after cmd.exe /C in quotes
    // as well. See:
    // http://stackoverflow.com/questions/6376113/how-to-use-spaces-in-cmd
    private static void escapeWindows(List<String> cmd) {
        // If only 1 arg, it can be the command plus args together so
        // attempt to split as such.
        if (cmd.size() == 1) {
            String cmdLine = cmd.get(0);
            cmd.clear();
            cmd.addAll(Arrays.asList(translateCommandline(cmdLine)));
        }
        
        // At this point we know we have command + args, escape. 
        List<String> newCmd = new ArrayList<>();
        for (String arg : cmd) {
            if (StringUtils.contains(arg, ' ') 
                    && !arg.matches("^\\s*\".*\"\\s*$")) {
                newCmd.add('"' + arg + '"');
            } else {
                newCmd.add(arg);
            }
        }
        cmd.clear();
        cmd.addAll(newCmd);
    }

//    // Escape spaces with a backslash if not already escaped
//    private static void escapeNonWindows(List<String> cmd) {
//        // If only 1 arg, it can be the command plus args together so
//        // attempt to split as such.
//        if (cmd.size() == 1) {
//            String cmdLine = cmd.get(0);
//            cmd.clear();
//            cmd.addAll(Arrays.asList(translateCommandline(cmdLine)));
//        }
//        
//        // At this point we know we have command + args, escape. 
//        for (int i = 0; i < cmd.size(); i++) {
//            cmd.add(i, StringEscapeUtils.escapeXSI(cmd.remove(i)));
//            if (StringUtils.contains(cmd.get(i), ' ')) {
//                cmd.add(i, StringEscapeUtils.escapeXSI(cmd.remove(i)));
//            }
//        }
//    }

    private void wrapCommand(List<String> cmd) {
        if (SystemUtils.OS_NAME == null || !SystemUtils.IS_OS_WINDOWS) {
            return;
        }
        String[] prefixes; 
        if (SystemUtils.IS_OS_WINDOWS_95
                || SystemUtils.IS_OS_WINDOWS_98
                || SystemUtils.IS_OS_WINDOWS_ME) {
            prefixes = CMD_PREFIXES_WIN_LEGACY;
        } else {
            // NT, 2000, XP and up
            prefixes = CMD_PREFIXES_WIN_CURRENT;
        }
        removePrefixes(cmd, prefixes);
        
        String wrappedCmd = "\"" + StringUtils.join(cmd, " ") + "\"";
        cmd.clear();
        cmd.addAll(Arrays.asList(prefixes));
        cmd.add(wrappedCmd);
    }

    //XXX This method is taken from Apache Commons Exec "CommandLine" class.
    //XXX It shall be removed once SystemCommand and related classes
    //XXX are replaced with Apache Commons Exec classes.
    //XXX https://commons.apache.org/proper/commons-exec/apidocs/src-html/org/apache/commons/exec/CommandLine.html
    private static String[] translateCommandline(final String toProcess) {
        if (toProcess == null || toProcess.length() == 0) {
            // no command? no string
            return new String[0];
        }

        // parse with a simple finite state machine

        final int normal = 0;
        final int inQuote = 1;
        final int inDoubleQuote = 2;
        int state = normal;
        final StringTokenizer tok = 
                new StringTokenizer(toProcess, "\"\' ", true);
        final ArrayList<String> list = new ArrayList<String>();
        StringBuilder current = new StringBuilder();
        boolean lastTokenHasBeenQuoted = false;

        while (tok.hasMoreTokens()) {
            final String nextTok = tok.nextToken();
            switch (state) {
            case inQuote:
                if ("\'".equals(nextTok)) {
                    lastTokenHasBeenQuoted = true;
                    state = normal;
                } else {
                    current.append(nextTok);
                }
                break;
            case inDoubleQuote:
                if ("\"".equals(nextTok)) {
                    lastTokenHasBeenQuoted = true;
                    state = normal;
                } else {
                    current.append(nextTok);
                }
                break;
            default:
                if ("\'".equals(nextTok)) {
                    state = inQuote;
                } else if ("\"".equals(nextTok)) {
                    state = inDoubleQuote;
                } else if (" ".equals(nextTok)) {
                    if (lastTokenHasBeenQuoted || current.length() != 0) {
                        list.add(current.toString());
                        current = new StringBuilder();
                    }
                } else {
                    current.append(nextTok);
                }
                lastTokenHasBeenQuoted = false;
                break;
            }
        }

        if (lastTokenHasBeenQuoted || current.length() != 0) {
            list.add(current.toString());
        }

        if (state == inQuote || state == inDoubleQuote) {
            throw new IllegalArgumentException("Unbalanced quotes in "
                    + toProcess);
        }

        final String[] args = new String[list.size()];
        return list.toArray(args);
    }
    
    private class ErrorTracker extends InputStreamLineListener {
        private final StringBuilder b = new StringBuilder();
        @Override
        protected void lineStreamed(String type, String line) {
            if (b.length() > 0) {
                b.append('\n');
            }
            b.append(line);
        }
    }
}