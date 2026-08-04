package com.innovarhealthcare.launcher;

import org.apache.commons.lang3.SystemUtils;
import org.junit.Assume;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class WindowsArgumentsTest {

    /** The cases from issue #165, plus the neighbouring characters that must not regress. */
    private static final String[] PASSWORDS = {
            "simple123",
            "pa\"ss",             // interior quote - the reported bug
            "\"quoted\"",         // leading and trailing quote
            "pa ss\"word",        // space and quote
            "tri\"p\"le\"quote",
            "back\\slash",
            "esc\\\"quote",       // backslash immediately before a quote
            "two\\\\\"quotes",
            "tail\\",
            "amp&pipe|caret^",
            "lt<gt>",
            "sp ace",
            "per%cent%",
            "P@ssw0rd!\"#$"
    };

    // --- quoting algorithm -------------------------------------------------

    @Test
    public void quoteWrapsAndEscapesInteriorQuotes() {
        assertEquals("\"pa\\\"ss\"", WindowsArguments.quote("pa\"ss"));
        assertEquals("\"\\\"quoted\\\"\"", WindowsArguments.quote("\"quoted\""));
        assertEquals("\"plain\"", WindowsArguments.quote("plain"));
    }

    @Test
    public void quoteDoublesBackslashesThatPrecedeAQuote() {
        // one backslash before a quote becomes two, so it reads as a literal backslash
        assertEquals("\"esc\\\\\\\"quote\"", WindowsArguments.quote("esc\\\"quote"));
        // backslashes not in front of a quote are left alone
        assertEquals("\"back\\slash\"", WindowsArguments.quote("back\\slash"));
    }

    @Test
    public void quoteDoublesTrailingBackslashesSoTheyDoNotEscapeTheClosingQuote() {
        assertEquals("\"tail\\\\\"", WindowsArguments.quote("tail\\"));
        assertEquals("\"tail\\\\\\\\\"", WindowsArguments.quote("tail\\\\"));
    }

    // --- protect() gating --------------------------------------------------

    @Test
    public void protectLeavesArgumentsWithoutQuotesAlone() {
        assertEquals("sp ace", WindowsArguments.protect("sp ace"));
        assertEquals("back\\slash", WindowsArguments.protect("back\\slash"));
        assertEquals("amp&pipe|caret^", WindowsArguments.protect("amp&pipe|caret^"));
        assertEquals(null, WindowsArguments.protect(null));
        assertEquals("", WindowsArguments.protect(""));
    }

    @Test
    public void protectRewritesQuotedArgumentsOnWindowsOnly() {
        String protectedArg = WindowsArguments.protect("pa\"ss");
        if (SystemUtils.IS_OS_WINDOWS && WindowsArguments.isLegacyQuotingActive()) {
            assertEquals("\"pa\\\"ss\"", protectedArg);
        } else {
            assertEquals("pa\"ss", protectedArg);
        }
    }

    @Test
    public void protectStandsDownWhenProcessImplDoesTheEscapingItself() {
        String previous = System.getProperty("jdk.lang.Process.allowAmbiguousCommands");
        System.setProperty("jdk.lang.Process.allowAmbiguousCommands", "false");
        try {
            assertEquals("pa\"ss", WindowsArguments.protect("pa\"ss"));
        } finally {
            if (previous == null) {
                System.clearProperty("jdk.lang.Process.allowAmbiguousCommands");
            } else {
                System.setProperty("jdk.lang.Process.allowAmbiguousCommands", previous);
            }
        }
    }

    // --- the real thing: does the child actually receive the password? -----

    /**
     * Spawns a JVM the same way {@code ProcessLauncher} does and checks every password arrives
     * byte-for-byte. Windows only - everywhere else the argument vector is passed straight through
     * and there is nothing to verify.
     */
    @Test
    public void passwordsSurviveARealProcessLaunch() throws Exception {
        Assume.assumeTrue("Windows-only: elsewhere argv is passed through as-is", SystemUtils.IS_OS_WINDOWS);

        List<String> mangled = new ArrayList<String>();
        for (String password : PASSWORDS) {
            String received = launchAndEcho("someuser", password);
            if (!password.equals(received)) {
                mangled.add("sent <" + password + "> but child got <" + received + ">");
            }
        }
        assertTrue("Arguments were mangled on the way to the child process: " + mangled, mangled.isEmpty());
    }

    private String launchAndEcho(String username, String password) throws Exception {
        List<String> command = new ArrayList<String>();
        // "java.exe", matching JavaConfig#getJavaHomeBuilder - this test is Windows-only, and
        // relying on CreateProcess to infer the extension would not mirror the production launch.
        command.add(System.getProperty("java.home") + File.separator + "bin" + File.separator + "java.exe");
        command.add("-cp");
        command.add(testClassesDirectory());
        command.add(ArgumentEchoMain.class.getName());
        command.add(username);
        command.add(password);

        // Exactly what ProcessLauncher does before handing the list to ProcessBuilder.
        for (int i = 1; i < command.size(); i++) {
            command.set(i, WindowsArguments.protect(command.get(i)));
        }

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        List<String> echoed = new ArrayList<String>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith(ArgumentEchoMain.PREFIX) && line.endsWith(ArgumentEchoMain.SUFFIX)) {
                    echoed.add(line.substring(ArgumentEchoMain.PREFIX.length(),
                            line.length() - ArgumentEchoMain.SUFFIX.length()));
                }
            }
        } finally {
            reader.close();
        }
        process.waitFor();

        return echoed.size() == 2 ? echoed.get(1) : "<child echoed " + echoed.size() + " argument(s): " + echoed + ">";
    }

    private String testClassesDirectory() throws Exception {
        return new File(ArgumentEchoMain.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getAbsolutePath();
    }
}
