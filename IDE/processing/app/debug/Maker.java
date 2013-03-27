/*
  Maker - default compiler class that connects to avr-gcc 
  Part of the Wiring project - http://wiring.org.co

  Copyright (c) 2013 Bryan Newbold
  TODO: copy back in old copyright, due to message() code

  This program is free software; you can redistribute it and/or modify
  it under the terms of the GNU General Public License as published by
  the Free Software Foundation; either version 2 of the License, or
  (at your option) any later version.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with this program; if not, write to the Free Software Foundation,
  Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/

package processing.app.debug;

import processing.app.*;
import processing.core.*;

import java.io.*;
import java.util.*;
import java.util.zip.*;

public class Maker implements MessageConsumer {

    Sketch sketch;
    boolean verbose;

    // TODO: won't build without this not sure what it's for
    RunnerException exception;
    String buildPath;

    public Maker(Sketch sketch) {
        this.sketch = sketch;
        this.verbose = false;
    }

    public boolean compile(String buildPath,
                           String primaryClassName,
                           boolean verbose) throws RunnerException {
        this.verbose = verbose || Preferences.getBoolean("build.verbose");

        //TODO:String armBasePath = Base.getArmBasePath();
        this.buildPath = buildPath;
        Map<String, String> boardPreferences = Base.getBoardPreferences();
        String core = boardPreferences.get("build.core");
        if (core == null) {
            RunnerException re = new RunnerException(
                "The board selected has no core specified, check your " +
                "board's definition of build.core.");
            re.hideStackTrace();
            throw re;
        }

        // coresPath points to the location of the architecture/platform
        // specific implementation of the Wiring C++ framework
        File coresFolder = Base.coresTable.get(core);
        String coresPath = coresFolder.getAbsolutePath();

        // build up an env list
        String[] env_keys = new String[4];
        String[] env_vals = new String[4];
        env_keys[0] = "LIB_MAPLE_HOME";
        env_vals[0] = coresPath;
        env_keys[1] = "BUILD_PATH";
        env_vals[1] = buildPath;
        env_keys[2] = "BOARD";
        env_vals[2] = "maple";
        env_keys[3] = "MEMORY_TARGET";
        env_vals[3] = "flash";
        //env_keys[] = "WIRISH_PATH";
        //env_keys[] = "LIBRARIES_PATH";

        // TODO: mkdir any output directories

        // TODO: copy makefile to buildpath
        // TODO: copy over makefile
        // TODO: call "make" with env all set up nicely
    	RunnerException re = new RunnerException("Unimplemented (yet).");
        re.hideStackTrace();
        throw re;
        //return false;

        /*
        // this is the (absolute?) path to the <toolchain>/bin folder
        String armBasePath = Base.getARMBasePath();
        // this is the path to the Board-specific folder containing, eg, pin
        // out tables
        String hardwarePath;
        if (hardware.indexOf(':') == -1) {
            Target t = Base.getTarget();
            File hardwareFolder = new File(t.getFolder(), hardware);
            hardwarePath = hardwareFolder.getAbsolutePath();
        } else {
            Target t = Base.targetsTable.get(hardware.substring(0, hardware.indexOf(':')));
            File hardwaresFolder = t.getFolder();
            File hardwareFolder = new File(hardwaresFolder, hardware.substring(hardware.indexOf(':') + 1));
            hardwarePath = hardwareFolder.getAbsolutePath();
        }
        
        String core = boardPreferences.get("build.core");

        if (core == null) {
            RunnerException re = new RunnerException("The board selected " +
                "has no core specified, check your board's definition of " +
                "build.core.");
            re.hideStackTrace();
            throw re;
        }
       
        // coresPath points to the location of the architecture/platform
        // specific implementation of the Wiring C++ framework
        File coresFolder = Base.coresTable.get(core);
        String coresPath = coresFolder.getAbsolutePath();

        // coresPath points to the location of the architecture/platform
        // NON-specific bits of the Wiring C++ framework
        File coresCommonFolder = Base.coresTable.get("Common");
        String coresCommonPath = coresCommonFolder.getAbsolutePath();
        */
    }

    private void callMake(String command, String[] env_keys, String[] env_vals,
                File build_path) {

        // verbosely print
        /*
        sketch.setCompilingProgress(10);
        File buildDirFile = new File(buildPath);
        String[] env_list = new String[2];
        //env_list[0] = "BOARD_INCLUDE_DIR=" + hardwarePath;
        callMake("build", env_list, buildDirFile, verbose);
        sketch.setCompilingProgress(90);
        return true;    

        try {
            process = Runtime.getRuntime().exec(cmd_list, env_list, build_dir);
        } catch (IOException e) {
            RunnerException re = new RunnerException(e.getMessage());
            re.hideStackTrace();
            throw re;
        }
        MessageSiphon in = new MessageSiphon(process.getInputStream(), this);
        MessageSiphon err = new MessageSiphon(process.getErrorStream(), this);
        
        // wait for the process to finish.  if interrupted
        // before waitFor returns, continue waiting
        boolean compiling = true;
        while (compiling) {
            try {
                if (in.thread != null)
                    in.thread.join();
                if (err.thread != null)
                    err.thread.join();
                result = process.waitFor();
                //System.out.println("result is " + result);
                compiling = false;
            } catch (InterruptedException ignored) { }
        }
        if (exception != null) { throw exception; }
        
        if (result > 1) {
            // a failure in the tool (e.g. unable to locate a sub-executable)
            System.err.println("make returned " + result);
        }
        
        if (result != 0) {
            RunnerException re = new RunnerException("Error compiling.");
            re.hideStackTrace();
            throw re;
        }
        */
    }

    /**
    * Part of the MessageConsumer interface, this is called
    * whenever a piece (usually a line) of error message is spewed
    * out from the compiler. The errors are parsed for their contents
    * and line number, which is then reported back to Editor.
    */
    public void message(String s) {
        int i;
        
        // remove the build path so people only see the filename
        // can't use replaceAll() because the path may have characters in it which
        // have meaning in a regular expression.
        if (!verbose) {
            while ((i = s.indexOf(buildPath + File.separator)) != -1) {
                s = s.substring(0, i) + s.substring(i + (buildPath + File.separator).length());
            }
        }
        
        // look for error line, which contains file name, line number,
        // and at least the first line of the error message
        String errorFormat = "([\\w\\d_]+.\\w+):(\\d+):\\s*\\s*(.*)\\s*";
        String[] pieces = PApplet.match(s, errorFormat);
        
        if (pieces != null) {
            RunnerException e = sketch.placeException(pieces[3], pieces[1], PApplet.parseInt(pieces[2]) - 1);
            
            // replace full file path with the name of the sketch tab (unless we're
            // in verbose mode, in which case don't modify the compiler output)
            if (e != null && !verbose) {
                SketchCode code = sketch.getCode(e.getCodeIndex());
                String fileName = code.isExtension(sketch.getDefaultExtension()) ? code.getPrettyName() : code.getFileName();
                s = fileName + ":" + e.getCodeLine() + ": error: " + e.getMessage();        
            }
                    
            if (exception == null && e != null) {
                exception = e;
                exception.hideStackTrace();
            }
        }
        System.err.print(s);
    }

    static private void createFolder(File folder) throws RunnerException {
        if (folder.isDirectory()) return;
        if (!folder.mkdir()) {
            throw new RunnerException("Couldn't create: " + folder);
        }
    }

}
