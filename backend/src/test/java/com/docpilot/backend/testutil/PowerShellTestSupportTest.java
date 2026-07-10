package com.docpilot.backend.testutil;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PowerShellTestSupportTest {

    @Test
    void usesWindowsPowerShellOnWindows() {
        assertEquals("powershell", PowerShellTestSupport.executableFor("Windows 11"));
    }

    @Test
    void usesPowerShellCoreOnNonWindowsRunners() {
        assertEquals("pwsh", PowerShellTestSupport.executableFor("Linux"));
        assertEquals("pwsh", PowerShellTestSupport.executableFor("Mac OS X"));
    }
}
