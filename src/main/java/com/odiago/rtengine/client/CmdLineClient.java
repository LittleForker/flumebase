// (c) Copyright 2010 Odiago, Inc.

package com.odiago.rtengine.client;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import java.util.Properties;

import org.apache.log4j.PropertyConfigurator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.odiago.rtengine.exec.ExecEnvironment;
import com.odiago.rtengine.exec.FlowId;
import com.odiago.rtengine.exec.QuerySubmitResponse;

import com.odiago.rtengine.exec.local.LocalEnvironment;
import com.odiago.rtengine.util.QuitException;

import jline.ConsoleReader;

/**
 * Client frontend to rtengine system.
 */
public class CmdLineClient {
  private static final String VERSION_STRING = "rtengine 1.0.0 / rtsql for Flume";

  private static final Logger LOG = LoggerFactory.getLogger(
      CmdLineClient.class.getName());

  /** True if we're mid-buffer on a command. */
  private boolean mInCommand;

  /** Buffer containing command being typed in over multiple lines. */
  private StringBuilder mCmdBuilder;

  /** The connected execution environment. */
  private ExecEnvironment mExecEnv;

  public CmdLineClient() {
    mExecEnv = new LocalEnvironment();
    resetCmdState();
  }

  /**
   * Print the version string to stdout.
   */
  private void printVersion() {
    System.out.println(VERSION_STRING);
  }

  private void printUsage() {
    System.out.println("");
    System.out.println("All text commands must end with a ';' character.");
    System.out.println("Session control commands must be on a line by themselves.");
    System.out.println("");
    System.out.println("Session control commands:");
    System.out.println("  \\c  Cancel the current input statement.");
    System.out.println("  \\h  Print help message.");
    System.out.println("  \\q  Quit the client.");
    System.out.println("");
  }

  /**
   * Reset the current command buffer.
   */
  private void resetCmdState() {
    mInCommand = false;
    mCmdBuilder = new StringBuilder();
  }

  /**
   * Handle a '\x' event for various values of the escape character 'x'.
   */
  private void handleEscape(char escapeChar) throws QuitException {
    switch(escapeChar) {
    case 'c':
      resetCmdState();
      break;
    case 'h':
      printUsage();
      break;
    case 'q':
      // Graceful quit from the shell.
      throw new QuitException(0);
    default:
      System.err.println("Unknown control command: \\" + escapeChar);
      break;
    }
  }

  /**
   * Given a command which may contain leading or trailing whitespace
   * and a final ';' at the end, remove these unnecessary characters.
   */
  private String trimTerminator(String cmdIn) {
    String trimmed = cmdIn.trim();
    if (trimmed.endsWith(";")) {
      // Remove the trailing ';' character.
      trimmed = trimmed.substring(0, trimmed.length() - 1);
      // After trimming the final ';', there may be additional whitespace to trim.
      trimmed = trimmed.trim();
    }

    return trimmed;
  }
  /**
   * Parse and execute a text command.
   */
  private void execCommand(String cmd) throws InterruptedException, IOException, QuitException {
    String realCommand = trimTerminator(cmd);
    
    if (realCommand.equalsIgnoreCase("help")) {
      printUsage();
    } else if (realCommand.equalsIgnoreCase("quit")) {
      throw new QuitException(0);
    } else {
      QuerySubmitResponse response = mExecEnv.submitQuery(realCommand);
      String msg = response.getMessage();
      if (null != msg) {
        System.out.println(msg);
      }

      FlowId flowId = response.getFlowId();
      if (null != flowId) {
        System.out.println("Started flow: " + flowId);
      }
    }
  }

  private void printPrompt() {
    if (mInCommand) {
      System.out.print("    -> ");
    } else {
      System.out.print("rtsql> ");
    }
  }

  private void initLogging() {
    // TODO: Pick a better default conf dir.
    // TODO: Put these up as final consts at the top of the class.
    String confDir = System.getProperty("rtengine.conf.dir", ".");
    File confDirFile = new File(confDir);
    File log4jPropertyFile = new File(confDirFile, "log4j.properties");
    FileInputStream fis = null;
    try {
      // Load the properties from the file first.
      fis = new FileInputStream(log4jPropertyFile);
      Properties props = new Properties();
      props.load(fis);

      // Overlay the system properties on top of the file, in case the
      // user wants to override the file settings at invocation time.
      Properties sysProps = System.getProperties();
      props.putAll(sysProps);
      PropertyConfigurator.configure(props);
      LOG.debug("Logging enabled.");
    } catch (IOException ioe) {
      System.err.println("IOException encoutered while initializing logging.");
      System.err.println("The logging system might not be ready, meaning you");
      System.err.println("might miss many other messages. Do you have an");
      System.err.println("etc/log4j.properties file in this installation?");
      System.err.println("Specific exception follows:");
      System.err.println(ioe);
    } finally {
      try {
        fis.close();
      } catch (IOException ioe) {
        LOG.warn("Exception closing log4j.properties file: " + ioe);
      }
    }

  }

  /**
   * Main entry point for the command-line client.
   * @return the exit code for the program (0 on success).
   */
  public int run() throws IOException {
    initLogging();
    System.out.println("Welcome to the rtengine client.");
    printVersion();
    System.out.println("Type 'help;' or '\\h' for instructions.");
    System.out.println("Type 'exit;' or '\\q' to quit.");

    ConsoleReader conReader = new ConsoleReader();

    mInCommand = false;
    mCmdBuilder = new StringBuilder();

    try {
      while (true) {
        printPrompt();
        String line = conReader.readLine();
        if (null == line) {
          // EOF on input. We're done.
          throw new QuitException(0);
        }
        String trimmed = line.trim();
        if (trimmed.startsWith("\\")) {
          handleEscape(line.charAt(1));
          resetCmdState();
        } else if (trimmed.endsWith(";")) {
          mCmdBuilder.append(line);
          mCmdBuilder.append('\n');
          try {
            execCommand(mCmdBuilder.toString());
          } catch (InterruptedException ie) {
            LOG.warn("Interrupted while processing command: " + ie);
          }
          resetCmdState();
        } else if (line.length() > 0) {
          mCmdBuilder.append(line);
          mCmdBuilder.append('\n');
          mInCommand = true;
        }
      }
    } catch (QuitException qe) {
      System.out.println("Goodbye");
      return qe.getStatus();
    } finally {
      try {
        mExecEnv.disconnect();
      } catch (InterruptedException ie) {
        LOG.warn("Interruption while disconnecting from service: " + ie);
      }
    }
  }

  public static void main(String [] args) throws Exception {
    CmdLineClient client = new CmdLineClient();
    System.exit(client.run());
  }
}
