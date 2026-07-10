package com.docpilot.backend.testutil;

import java.util.Locale;

public final class PowerShellTestSupport {

    private PowerShellTestSupport() {
    }

    public static String executable() {
        return executableFor(System.getProperty("os.name", ""));
    }

    static String executableFor(String osName) {
        return osName.toLowerCase(Locale.ROOT).contains("windows") ? "powershell" : "pwsh";
    }
}
