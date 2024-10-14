/*
 * Copyright (c) 2011, 2021, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */

package com.sun.webkit.network;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.IDN;
import java.net.URI;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * A collection of static utility methods dealing with "public suffixes".
 */
final class PublicSuffixes {

    private static final Logger logger =
            Logger.getLogger(PublicSuffixes.class.getName());


    /**
     * Public suffix list rule types.
     */
    private enum Rule {
        SIMPLE_RULE,
        WILDCARD_RULE,
        EXCEPTION_RULE,
    }


    /**
     * The mapping from top-level domain names to public suffix list rules.
     */
    private static final Map<String, Rules> rulesCache = new ConcurrentHashMap<>();


    /**
     * The public suffix list file.
     */
    private static final File pslFile = AccessController.doPrivileged((PrivilegedAction<File>)
        () -> new File(getLibDir(), "../security/public_suffix_list.dat"));


    /*
     * Determines whether the public suffix list file is available.
     */
    private static final boolean pslFileExists = AccessController.doPrivileged(
        (PrivilegedAction<Boolean>) () -> {
            if (!pslFile.exists()) {
                logger.log(Level.WARNING, "Resource not found: " +
                    "lib/security/public_suffix_list.dat");
                return false;
            }
            return true;
        });


    /**
     * The private default constructor. Ensures non-instantiability.
     */
    private PublicSuffixes() {
        throw new AssertionError();
    }


    /**
     * Returns whether the public suffix list file is available.
     */
    static boolean pslFileExists() {
        return pslFileExists;
    }


    /**
     * Determines if a domain is a public suffix.
     */
    static boolean isPublicSuffix(String domain) {
        if (domain.length() == 0) {
            return false;
        }

        if (!pslFileExists()) {
            return false;
        }

        Rules rules = Rules.getRules(domain);
        return rules == null ? false : rules.match(domain);
    }

    private static File getLibDir() {
        try {
            // Get the URL for this class, if it is a jar URL, then get the
            // filename associated with it.
            String theClassFile = "PublicSuffixes.class";
            Class theClass = PublicSuffixes.class;
            String classUrlString = theClass.getResource(theClassFile).toString();
            if (!classUrlString.startsWith("jar:file:") || classUrlString.indexOf('!') == -1){
                return null;
            }
            // Strip out the "jar:" and everything after and including the "!"
            String tmpStr = classUrlString.substring(4, classUrlString.lastIndexOf('!'));
            // Strip everything after the last "/" or "\" to get rid of the jar filename
            int lastIndexOfSlash = Math.max(tmpStr.lastIndexOf('/'), tmpStr.lastIndexOf('\\'));

            // Location of public suffix list relative to jar file
            String pslFileString = tmpStr.substring(0, lastIndexOfSlash);
            return new File(new URI(pslFileString).getPath());
        } catch (Exception e) {
            return null;
        }
    }

    private static class Rules {

        private final Map<String, Rule> rules = new HashMap<>();

        private Rules(InputStream is) throws IOException {
            InputStreamReader isr = new InputStreamReader(is, "UTF-8");
            BufferedReader reader = new BufferedReader(isr);

            String line;
            int type = reader.read();
            while (type != -1 && (line = reader.readLine()) != null) {
                Rule rule;
                if (line.startsWith("!")) {
                    line = line.substring(1);
                    rule = Rule.EXCEPTION_RULE;
                } else if (line.startsWith("*.")) {
                    line = line.substring(2);
                    rule = Rule.WILDCARD_RULE;
                } else {
                    rule = Rule.SIMPLE_RULE;
                }
                try {
                    line = IDN.toASCII(line, IDN.ALLOW_UNASSIGNED);
                } catch (Exception ex) {
                    logger.log(Level.WARNING, String.format(
                            "Error parsing rule: [%s]", line), ex);
                    continue;
                }
                rules.put(line, rule);
                type = reader.read();
            }
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, "rules: {0}", toLogString(rules));
            }
        }

        static Rules getRules(String domain) {
            String tld = getTopLevelDomain(domain);
            if (tld.isEmpty()) {
                return null;
            }
            return rulesCache.computeIfAbsent(tld, k -> createRules(tld));
        }

        private static String getTopLevelDomain(String domain) {
            domain = IDN.toUnicode(domain, IDN.ALLOW_UNASSIGNED);
            int n = domain.lastIndexOf('.');
            if (n == -1) {
                return domain;
            }
            return domain.substring(n + 1);
        }

        private static Rules createRules(String tld) {
            try (InputStream pubSuffixStream = getPubSuffixStream()) {
                if (pubSuffixStream == null) {
                    return null;
                }
                ZipInputStream zis = new ZipInputStream(pubSuffixStream);
                ZipEntry ze = zis.getNextEntry();
                while (ze != null) {
                    if (ze.getName().equals(tld)) {
                        return new Rules(zis);
                    } else {
                        ze = zis.getNextEntry();
                    }
                }
            } catch (IOException ex) {
                logger.log(Level.WARNING, "Unexpected error", ex);
            }
            return null;
        }

        private static InputStream getPubSuffixStream() {
            InputStream is = AccessController.doPrivileged(
                (PrivilegedAction<InputStream>) () -> {
                    try {
                        return new FileInputStream(pslFile);
                    } catch (FileNotFoundException ex) {
                        logger.log(Level.WARNING, "Resource not found: " +
                                "lib/security/public_suffix_list.dat");
                        return null;
                    }
                }
            );
            return is;
        }

        boolean match(String domain) {
            Rule rule = rules.get(domain);
            if (rule == Rule.EXCEPTION_RULE) {
                return false;
            } else if (rule == Rule.SIMPLE_RULE || rule == Rule.WILDCARD_RULE) {
                return true;
            } else {
                int pos = domain.indexOf('.') + 1;
                if (pos == 0) {
                    pos = domain.length();
                }
                String parent = domain.substring(pos);
                return rules.get(parent) == Rule.WILDCARD_RULE;
            }
        }
    }

    /**
     * Converts a map of rules to a string suitable for displaying
     * in the log.
     */
    private static String toLogString(Map<String,Rule> rules) {
        if (rules.isEmpty()) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String,Rule> entry : rules.entrySet()) {
            sb.append(String.format("%n    "));
            sb.append(entry.getKey());
            sb.append(": ");
            sb.append(entry.getValue());
        }
        return sb.toString();
    }
}
