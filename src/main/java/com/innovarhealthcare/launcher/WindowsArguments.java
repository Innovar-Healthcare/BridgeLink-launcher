package com.innovarhealthcare.launcher;

import org.apache.commons.lang3.SystemUtils;

/**
 * Keeps command line arguments intact on Windows.
 *
 * <p>Windows has no argv. {@link ProcessBuilder} hands the OS a single command line string, and
 * {@code java.lang.ProcessImpl} is what turns the argument list back into that string. In its
 * default ("legacy") mode ProcessImpl only wraps an argument in quotes when it contains a space or
 * a tab, and it never escapes a quote <em>inside</em> an argument. The child then splits the
 * command line again and the quote is consumed: a password of {@code pa"ss} arrives as
 * {@code pass}, and {@code "secret"} arrives as {@code secret}.
 *
 * <p>That is BridgeLink issue #165 - a saved password containing {@code "} is not forwarded to the
 * Administrator login.
 *
 * <p>The fix is to quote the argument ourselves using the algorithm {@code CommandLineToArgvW}
 * uses to split a command line, because ProcessImpl leaves an argument alone when it already starts
 * and ends with a quote. The pre-quoted form therefore reaches the child verbatim and unwinds back
 * to the original text.
 *
 * <p>Only arguments that actually contain a quote are rewritten. Backslashes, spaces, and the shell
 * metacharacters {@code & | ^ < >} already survive ProcessImpl unharmed, so leaving them untouched
 * keeps this change to the one case that is broken.
 */
public final class WindowsArguments {

    private static final char QUOTE = '"';
    private static final char BACKSLASH = '\\';

    private WindowsArguments() {}

    /**
     * Returns {@code arg} in a form that reaches the child process unchanged.
     *
     * <p>A no-op off Windows (where the argument vector is passed through as-is) and a no-op for
     * arguments that contain no quote.
     */
    public static String protect(String arg) {
        if (arg == null || arg.indexOf(QUOTE) < 0) {
            return arg;
        }
        if (!SystemUtils.IS_OS_WINDOWS) {
            return arg;
        }
        if (!isLegacyQuotingActive()) {
            /*
             * ProcessImpl is in VERIFICATION_WIN32_SAFE mode and escapes interior quotes itself.
             * Pre-quoting on top of that is rejected outright ("Malformed argument has embedded
             * quote"), so hand the argument over untouched.
             */
            return arg;
        }
        return quote(arg);
    }

    /**
     * Applies {@code CommandLineToArgvW} quoting: the result always starts and ends with a quote,
     * every interior quote is escaped with a backslash, and every backslash that ends up in front
     * of a quote is doubled so it is read as a literal backslash rather than an escape.
     */
    static String quote(String arg) {
        StringBuilder quoted = new StringBuilder(arg.length() + 8);
        quoted.append(QUOTE);

        int pendingBackslashes = 0;
        for (int i = 0; i < arg.length(); i++) {
            char c = arg.charAt(i);
            if (c == BACKSLASH) {
                // Held back: whether these need doubling depends on what follows them.
                pendingBackslashes++;
            } else if (c == QUOTE) {
                appendBackslashes(quoted, pendingBackslashes * 2 + 1);
                pendingBackslashes = 0;
                quoted.append(QUOTE);
            } else {
                appendBackslashes(quoted, pendingBackslashes);
                pendingBackslashes = 0;
                quoted.append(c);
            }
        }

        // Backslashes immediately before the closing quote would otherwise escape it.
        appendBackslashes(quoted, pendingBackslashes * 2);
        quoted.append(QUOTE);
        return quoted.toString();
    }

    /**
     * Whether {@code ProcessImpl} will pass an already-quoted argument through verbatim, which is
     * what {@link #quote(String)} relies on. True unless the JVM was started with
     * {@code -Djdk.lang.Process.allowAmbiguousCommands=false} or runs under a security manager.
     */
    @SuppressWarnings({ "deprecation", "removal" })
    static boolean isLegacyQuotingActive() {
        if (System.getSecurityManager() != null) {
            return false;
        }
        return !"false".equalsIgnoreCase(System.getProperty("jdk.lang.Process.allowAmbiguousCommands", "true"));
    }

    private static void appendBackslashes(StringBuilder sb, int count) {
        for (int i = 0; i < count; i++) {
            sb.append(BACKSLASH);
        }
    }
}
