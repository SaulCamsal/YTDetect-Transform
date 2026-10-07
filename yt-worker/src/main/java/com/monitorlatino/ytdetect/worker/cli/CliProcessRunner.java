package com.monitorlatino.ytdetect.worker.cli;

import java.io.File;
import java.io.IOException;
import java.util.List;

public interface CliProcessRunner {

    ProcessResult runCommand(List<String> command, File workingDirectory) throws IOException, InterruptedException;
}
