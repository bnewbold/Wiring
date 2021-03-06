/* -*- mode: jde; r-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Compiler - default compiler class that connects to avr-gcc 
  Part of the Wiring project - http://wiring.org.co

  Copyright (c) 2004-11 Hernando Barragan

  Processing version
  Copyright (c) 2004-05 Ben Fry and Casey Reas
  Copyright (c) 2001-04 Massachusetts Institute of Technology

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
import javax.swing.*;

public class Compiler implements MessageConsumer {
  static final String BUGS_URL =
    "http://wiring.org.co/bugs/";
  static final String SUPER_BADNESS =
    "Compiler error, please submit this code to " + BUGS_URL;

  Sketch sketch;
  String buildPath;
  String primaryClassName;
  boolean verbose;

  RunnerException exception;

  public Compiler() { }  // consider this a warning, you werkin soon.


  public boolean compile(Sketch sketch, 
             String buildPath, 
             String primaryClassName, 
             boolean verbose) throws RunnerException {

    this.sketch = sketch;
    this.buildPath = buildPath;
    this.primaryClassName = primaryClassName;
    this.verbose = verbose;

    // the pms object isn't used for anything but storage
    MessageStream pms = new MessageStream(this);

    String avrBasePath = Base.getAvrBasePath();
    Map<String, String> boardPreferences = Base.getBoardPreferences();
    String hardware = boardPreferences.get("build.hardware");
    if (hardware == null) {
    	RunnerException re = new RunnerException("No board selected; please choose a board from the Tools > Board menu.");
      re.hideStackTrace();
      throw re;
    }
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
    String coresPath;
    String coresCommonPath;
    if (core == null) {
    	RunnerException re = new RunnerException("The board selected has no core specified, check your board's definition of build.core.");
      re.hideStackTrace();
      throw re;
    }
    
    File coresFolder = Base.coresTable.get(core);
    File coresCommonFolder = Base.coresTable.get("Common");
    coresPath = coresFolder.getAbsolutePath();
    coresCommonPath = coresCommonFolder.getAbsolutePath();
    
    List<File> objectFiles = new ArrayList<File>();
    
    List includePaths = new ArrayList();
    includePaths.add(coresCommonPath);
    includePaths.add(coresPath);
    includePaths.add(hardwarePath);
    includePaths.add(buildPath);
    
    
    sketch.setCompilingProgress(20);
    
    // use library directories as include paths for all libraries
    for (File file : sketch.getImportedLibraries()) {
      includePaths.add(file.getPath());
    }

    // 1. compile the sketch (already in the buildPath)
    
    objectFiles.addAll(
                       compileFiles(avrBasePath, buildPath, includePaths,
                                    findFilesInPath(buildPath, "S", false),
                                    findFilesInPath(buildPath, "c", false),
                                    findFilesInPath(buildPath, "cpp", false),
                                    boardPreferences));
    
    sketch.setCompilingProgress(30);
    
    // 2. compile the libraries, outputting .o files to: <buildPath>/<library>/
    for (File libraryFolder : sketch.getImportedLibraries()) {
      File outputFolder = new File(buildPath, libraryFolder.getName());
      File utilityFolder = new File(libraryFolder, "utility");
      createFolder(outputFolder);
      // this library can use includes in its utility/ folder
      includePaths.add(utilityFolder.getAbsolutePath());
      
      // Barragan: libraries might depend on other libraries, so add all paths for compilation
      objectFiles.addAll(
      compileFiles(avrBasePath, outputFolder.getAbsolutePath(), includePaths,
        findFilesInFolder(libraryFolder, "S", false),
        findFilesInFolder(libraryFolder, "c", false),
        findFilesInFolder(libraryFolder, "cpp", false),
        boardPreferences));
      outputFolder = new File(outputFolder, "utility");
      createFolder(outputFolder);
      objectFiles.addAll(
        compileFiles(avrBasePath, outputFolder.getAbsolutePath(), includePaths,
        findFilesInFolder(utilityFolder, "S", false),
        findFilesInFolder(utilityFolder, "c", false),
        findFilesInFolder(utilityFolder, "cpp", false),
        boardPreferences));
      // other libraries should not see this library's utility/ folder
      includePaths.remove(includePaths.size() - 1);
    }
    sketch.setCompilingProgress(40);

    
    // 3a. compile the hardware, outputting .o files to <buildPath> 
    includePaths.clear();
    includePaths.add(hardwarePath);
    includePaths.add(coresCommonPath);
    includePaths.add(coresPath);
    
    List<File> hardwareObjectFiles = 
    compileFiles(avrBasePath, buildPath, includePaths,
                 findFilesInPath(hardwarePath, "S", true),
                 findFilesInPath(hardwarePath, "c", true),
                 findFilesInPath(hardwarePath, "cpp", true),
                 boardPreferences);
    
    sketch.setCompilingProgress(50);
    
    // 3b. compile the core, outputting .o files to <buildPath> and then
    // collecting them into the core.a library file.
    includePaths.clear();
    includePaths.add(hardwarePath);
    includePaths.add(coresCommonPath);
    includePaths.add(coresPath);
    
    List<File> coresObjectFiles = new ArrayList<File>();
    coresObjectFiles.addAll(
      compileFiles(avrBasePath, buildPath, includePaths,
      findFilesInPath(coresPath, "S", false),
      findFilesInPath(coresPath, "c", false),
      findFilesInPath(coresPath, "cpp", false),
      boardPreferences));
    sketch.setCompilingProgress(60);
    coresObjectFiles.addAll(
      compileFiles(avrBasePath, buildPath, includePaths,
      findFilesInPath(coresCommonPath, "S", false),
      findFilesInPath(coresCommonPath, "c", false),
      findFilesInPath(coresCommonPath, "cpp", false),
      boardPreferences));
    coresObjectFiles.addAll(hardwareObjectFiles);
    
    sketch.setCompilingProgress(70);
    
    String runtimeLibraryName = buildPath + File.separator + "core.a";
    List baseCommandAR = new ArrayList(Arrays.asList(new String[] {
      avrBasePath + "avr-ar",
      "rcs",
      runtimeLibraryName
    }));
    
    for(File file : coresObjectFiles) {
      List commandAR = new ArrayList(baseCommandAR);
      commandAR.add(file.getAbsolutePath());
      execAsynchronously(commandAR);
    }

    
        
    
    // 4. link it all together into the .elf file
    sketch.setCompilingProgress(80);
    List baseCommandLinker = new ArrayList(Arrays.asList(new String[] {
      avrBasePath + "avr-gcc",
      "-Os",
      "-Wl,--gc-sections",
      "-mmcu=" + boardPreferences.get("build.mcu"),
      "-o",
      buildPath + File.separator + primaryClassName + ".elf"
    }));
    
    for (File file : objectFiles) {
      baseCommandLinker.add(file.getAbsolutePath());
    }
    
    baseCommandLinker.add(runtimeLibraryName);
    baseCommandLinker.add("-L" + buildPath);
    baseCommandLinker.add("-lm");
    
    execAsynchronously(baseCommandLinker);
    sketch.setCompilingProgress(85);
    List baseCommandObjcopy = new ArrayList(Arrays.asList(new String[] {
      avrBasePath + "avr-objcopy",
      "-O",
      "-R",
    }));
    
    List commandObjcopy;
    
    // 5. extract EEPROM data (from EEMEM directive) to .eep file.
    commandObjcopy = new ArrayList(baseCommandObjcopy);
    commandObjcopy.add(2, "ihex");
    commandObjcopy.set(3, "-j");
    commandObjcopy.add(".eeprom");
    commandObjcopy.add("--set-section-flags=.eeprom=alloc,load");
    commandObjcopy.add("--no-change-warnings");
    commandObjcopy.add("--change-section-lma");
    commandObjcopy.add(".eeprom=0");
    commandObjcopy.add(buildPath + File.separator + primaryClassName + ".elf");
    commandObjcopy.add(buildPath + File.separator + primaryClassName + ".eep");
    execAsynchronously(commandObjcopy);
    
    // 6. build the .hex file
    commandObjcopy = new ArrayList(baseCommandObjcopy);
    commandObjcopy.add(2, "ihex");
    commandObjcopy.add(".eeprom"); // remove eeprom data
    commandObjcopy.add(buildPath + File.separator + primaryClassName + ".elf");
    commandObjcopy.add(buildPath + File.separator + primaryClassName + ".hex");
    execAsynchronously(commandObjcopy);
    sketch.setCompilingProgress(90);
    return true;    

  }
  
  private List<File> compileFiles(String avrBasePath,
                  String buildPath, List<File> includePaths,
                  List<File> sSources, 
                  List<File> cSources, List<File> cppSources,
                  Map<String, String> boardPreferences)
    throws RunnerException {
    
    List<File> objectPaths = new ArrayList<File>();
    
    for (File file : sSources) {
      String objectPath = buildPath + File.separator + file.getName() + ".o";
      objectPaths.add(new File(objectPath));
      execAsynchronously(getCommandCompilerS(avrBasePath, includePaths,
                         file.getAbsolutePath(),
                         objectPath,
                         boardPreferences));
    }
    
    for (File file : cSources) {
      String objectPath = buildPath + File.separator + file.getName() + ".o";
      objectPaths.add(new File(objectPath));
      execAsynchronously(getCommandCompilerC(avrBasePath, includePaths,
                         file.getAbsolutePath(),
                         objectPath,
                         boardPreferences));
    }
    
    for (File file : cppSources) {
      String objectPath = buildPath + File.separator + file.getName() + ".o";
      objectPaths.add(new File(objectPath));
      execAsynchronously(getCommandCompilerCPP(avrBasePath, includePaths,
                         file.getAbsolutePath(),
                         objectPath,
                         boardPreferences));
    }
    
    return objectPaths;
  }

  /**
   * Either succeeds or throws a RunnerException fit for public consumption.
   */
  private void execAsynchronously(List commandList) throws RunnerException {
    String[] command = new String[commandList.size()];
    commandList.toArray(command);
    int result = 0;
    
    if (verbose || Preferences.getBoolean("build.verbose")) {
      for(int j = 0; j < command.length; j++) {
        System.out.print(command[j] + " ");
      }
      System.out.println();
    }
    
    firstErrorFound = false;  // haven't found any errors yet
    secondErrorFound = false;
    
    Process process;
    
    try {
      process = Runtime.getRuntime().exec(command);
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
    
    // an error was queued up by message(), barf this back to compile(),
    // which will barf it back to Editor. if you're having trouble
    // discerning the imagery, consider how cows regurgitate their food
    // to digest it, and the fact that they have five stomaches.
    //
    //System.out.println("throwing up " + exception);
    if (exception != null) { throw exception; }
    
    if (result > 1) {
      // a failure in the tool (e.g. unable to locate a sub-executable)
      System.err.println(command[0] + " returned " + result);
    }
    
    if (result != 0) {
      RunnerException re = new RunnerException("Error compiling.");
      re.hideStackTrace();
      throw re;
    }
  }

  boolean firstErrorFound;
  boolean secondErrorFound;

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
  

  static private List getCommandCompilerS(String avrBasePath, List includePaths,
    String sourceName, String objectName, Map<String, String> boardPreferences) {
    
    List baseCommandCompiler = new ArrayList(Arrays.asList(new String[] {
      avrBasePath + "avr-gcc",
      "-c", // compile, don't link
      "-g", // include debugging info (so errors include line numbers)
      "-assembler-with-cpp",
      "-mmcu=" + boardPreferences.get("build.mcu"),
      "-DF_CPU=" + boardPreferences.get("build.f_cpu"),
      "-DWIRING=" +Base.REVISION,
    }));

    for (int i = 0; i < includePaths.size(); i++) {
      baseCommandCompiler.add("-I" + (String) includePaths.get(i));
    }

    baseCommandCompiler.add(sourceName);
    baseCommandCompiler.add("-o"+ objectName);

    return baseCommandCompiler;
  }


  static private List getCommandCompilerC(String avrBasePath, List includePaths,
    String sourceName, String objectName, Map<String, String> boardPreferences) {

    List baseCommandCompiler = new ArrayList(Arrays.asList(new String[] {
      avrBasePath + "avr-gcc",
      "-c", // compile, don't link
      "-g", // include debugging info (so errors include line numbers)
      "-Os", // optimize for size
      "-w", // surpress all warnings
      "-ffunction-sections", // place each function in its own section
      "-fdata-sections",
      "-mmcu=" + boardPreferences.get("build.mcu"),
      "-DF_CPU=" + boardPreferences.get("build.f_cpu"),
      "-DWIRING=" + Base.REVISION,
    }));
                
    for (int i = 0; i < includePaths.size(); i++) {
      baseCommandCompiler.add("-I" + (String) includePaths.get(i));
    }

    baseCommandCompiler.add(sourceName);
    baseCommandCompiler.add("-o"+ objectName);

    return baseCommandCompiler;
  }


  static private List getCommandCompilerCPP(String avrBasePath,
    List includePaths, String sourceName, String objectName,
    Map<String, String> boardPreferences) {
    
    List baseCommandCompilerCPP = new ArrayList(Arrays.asList(new String[] {
      avrBasePath + "avr-g++",
      "-c", // compile, don't link
      "-g", // include debugging info (so errors include line numbers)
      "-Os", // optimize for size
      "-w", // surpress all warnings
      "-fno-exceptions",
      "-ffunction-sections", // place each function in its own section
      "-fdata-sections",
      "-mmcu=" + boardPreferences.get("build.mcu"),
      "-DF_CPU=" + boardPreferences.get("build.f_cpu"),
      "-DWIRING=" + Base.REVISION,
    }));

    for (int i = 0; i < includePaths.size(); i++) {
      baseCommandCompilerCPP.add("-I" + (String) includePaths.get(i));
    }

    baseCommandCompilerCPP.add(sourceName);
    baseCommandCompilerCPP.add("-o"+ objectName);

    return baseCommandCompilerCPP;
  }


  static private void createFolder(File folder) throws RunnerException {
    if (folder.isDirectory()) return;
    if (!folder.mkdir())
      throw new RunnerException("Couldn't create: " + folder);
  }


  static public ArrayList<File> findFilesInPath(String path, String extension,
                          boolean recurse) {
    return findFilesInFolder(new File(path), extension, recurse);
  }
  
  static public ArrayList<File> findFilesInFolder(File folder, String extension,
                          boolean recurse) {
    ArrayList<File> files = new ArrayList<File>();
    
    if (folder.listFiles() == null) return files;
    
    for (File file : folder.listFiles()) {
      if (file.getName().equals(".") || file.getName().equals("..")) continue;
      
      if (file.getName().endsWith("." + extension))
        files.add(file);
      
      if (recurse && file.isDirectory()) {
        files.addAll(findFilesInFolder(file, extension, true));
      }
    }
    
    return files;
  }
  
  /**
   * A classpath, separated by the path separator, will contain
   * a series of .jar/.zip files or directories containing .class
   * files, or containing subdirectories that have .class files.
   *
   * @param path the input classpath
   * @return array of possible package names
   */
  static public String[] packageListFromClassPath(String path) {
    Hashtable table = new Hashtable();
    String pieces[] =
    PApplet.split(path, File.pathSeparatorChar);
    
    for (int i = 0; i < pieces.length; i++) {
      if (pieces[i].length() == 0) continue;
      
      if (pieces[i].toLowerCase().endsWith(".jar") ||
        pieces[i].toLowerCase().endsWith(".zip")) {
        packageListFromZip(pieces[i], table);
        
      } else {  // it's another type of file or directory
        File dir = new File(pieces[i]);
        if (dir.exists() && dir.isDirectory()) {
          packageListFromFolder(dir, null, table);
        }
      }
    }
    int tableCount = table.size();
    String output[] = new String[tableCount];
    int index = 0;
    Enumeration e = table.keys();
    while (e.hasMoreElements()) {
      output[index++] = ((String) e.nextElement()).replace('/', '.');
    }
    return output;
  }
  
  
  static private void packageListFromZip(String filename, Hashtable table) {
    try {
      ZipFile file = new ZipFile(filename);
      Enumeration entries = file.entries();
      while (entries.hasMoreElements()) {
        ZipEntry entry = (ZipEntry) entries.nextElement();
        
        if (!entry.isDirectory()) {
          String name = entry.getName();
          
          if (name.endsWith(".class")) {
            int slash = name.lastIndexOf('/');
            if (slash == -1) continue;
            
            String pname = name.substring(0, slash);
            if (table.get(pname) == null) {
              table.put(pname, new Object());
            }
          }
        }
      }
    } catch (IOException e) {
      System.err.println("Ignoring " + filename + " (" + e.getMessage() + ")");
    }
  }

  /**
   * Given a folder, return a list of the header files in that folder (but
   * not the header files in its sub-folders, as those should be included from
   * within the header files at the top-level).
   */
  static public String[] headerListFromIncludePath(String path) {
    FilenameFilter onlyHFiles = new FilenameFilter() {
      public boolean accept(File dir, String name) {
        return name.endsWith(".h");
      }
    };
    
    return (new File(path)).list(onlyHFiles);
  }

  /**
   * Given a folder, return a list of absolute paths to all jar or zip files
   * inside that folder, separated by pathSeparatorChar.
   *
   * This will prepend a colon (or whatever the path separator is)
   * so that it can be directly appended to another path string.
   *
   * As of 0136, this will no longer add the root folder as well.
   *
   * This function doesn't bother checking to see if there are any .class
   * files in the folder or within a subfolder.
   */
  static public String contentsToClassPath(File folder) {
    if (folder == null) return "";
    
    StringBuffer abuffer = new StringBuffer();
    String sep = System.getProperty("path.separator");
    
    try {
      String path = folder.getCanonicalPath();
      
      // When getting the name of this folder, make sure it has a slash
      // after it, so that the names of sub-items can be added.
      if (!path.endsWith(File.separator)) {
        path += File.separator;
      }
      
      String list[] = folder.list();
      for (int i = 0; i < list.length; i++) {
        // Skip . and ._ files. Prior to 0125p3, .jar files that had
        // OS X AppleDouble files associated would cause trouble.
        if (list[i].startsWith(".")) continue;
        
        if (list[i].toLowerCase().endsWith(".jar") ||
          list[i].toLowerCase().endsWith(".zip")) {
          abuffer.append(sep);
          abuffer.append(path);
          abuffer.append(list[i]);
        }
      }
    } catch (IOException e) {
      e.printStackTrace();  // this would be odd
    }
    return abuffer.toString();
  }
  
  
  /**
   * Make list of package names by traversing a directory hierarchy.
   * Each time a class is found in a folder, add its containing set
   * of folders to the package list. If another folder is found,
   * walk down into that folder and continue.
   */
  static private void packageListFromFolder(File dir, String sofar,
                        Hashtable table) {
    boolean foundClass = false;
    String files[] = dir.list();
    
    for (int i = 0; i < files.length; i++) {
      if (files[i].equals(".") || files[i].equals("..")) continue;
      
      File sub = new File(dir, files[i]);
      if (sub.isDirectory()) {
        String nowfar =
        (sofar == null) ? files[i] : (sofar + "." + files[i]);
        packageListFromFolder(sub, nowfar, table);
      } else if (!foundClass) {  // if no classes found in this folder yet
        if (files[i].endsWith(".class")) {
          table.put(sofar, new Object());
          foundClass = true;
        }
      }
    }
  }
}
