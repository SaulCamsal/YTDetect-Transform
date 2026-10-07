package com.monitorlatino.ytdetect.worker.cli;

public record ProcessResult(
        int exitCode,
        String output,
        String errorOutput
) {
    public boolean isSuccess() {
        return exitCode == 0;
    }
}
