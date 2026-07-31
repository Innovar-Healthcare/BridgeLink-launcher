package com.innovarhealthcare.launcher;

/**
 * Child process used by {@link WindowsArgumentsTest} to report the arguments it actually received.
 * Each argument is printed on its own line, wrapped in markers so trailing whitespace survives.
 */
public class ArgumentEchoMain {

    public static final String PREFIX = "<<<";
    public static final String SUFFIX = ">>>";

    public static void main(String[] args) {
        for (String arg : args) {
            System.out.println(PREFIX + arg + SUFFIX);
        }
    }
}
