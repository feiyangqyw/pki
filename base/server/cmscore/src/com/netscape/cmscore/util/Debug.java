// --- BEGIN COPYRIGHT BLOCK ---
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; version 2 of the License.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License along
// with this program; if not, write to the Free Software Foundation, Inc.,
// 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
//
// (C) 2007 Red Hat, Inc.
// All rights reserved.
// --- END COPYRIGHT BLOCK ---
package com.netscape.cmscore.util;

import java.util.Hashtable;
import java.util.StringTokenizer;

import org.dogtagpki.util.logging.PKILogger;

import com.netscape.certsrv.base.IConfigStore;
import com.netscape.certsrv.base.ISubsystem;
import com.netscape.cmscore.apps.CMS;

public class Debug
        implements ISubsystem {

    private static Debug mInstance = new Debug();

    public static final boolean ON = false;
    public static final int OBNOXIOUS = 1;
    public static final int VERBOSE = 5;
    public static final int INFORM = 10;

    // the difference between this and 'ON' is that this is always
    // guaranteed to log to 'mOut', whereas other parts of the server
    // may do:
    //  if (Debug.ON) {
    //     System.out.println("..");
    //	}
    // I want to make sure that any Debug.trace() is not logged to
    // System.out if the server is running under watchdog

    private static boolean TRACE_ON = false;

    private static Hashtable<String, String> mHK = null;

    private static char getNybble(byte b) {
        if (b < 10) {
            return (char)('0' + b);
        } else {
            return (char)('a' + b - 10);
        }
    }

    public static String dump(byte[] b) {

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < b.length; i++) {
            sb.append(getNybble((byte) ((b[i] & 0xf0) >> 4)));
            sb.append(getNybble((byte) (b[i] & 0x0f)));

            if (((i % 16) == 15) && i != b.length) {
                sb.append('\n');
            } else {
                sb.append(" ");
            }
        }

        return sb.toString();
    }

    /**
     * Set the current debugging level. You can use:
     *
     * <pre>
     * OBNOXIOUS = 1
     * VERBOSE   = 5
     * INFORM    = 10
     * </pre>
     *
     * Or another value
     */

    public static void setLevel(int level) {

        PKILogger.Level logLevel;

        if (level <= OBNOXIOUS) {
            logLevel = PKILogger.Level.TRACE;

        } else if (level <= VERBOSE) {
            logLevel = PKILogger.Level.DEBUG;

        } else if (level <= INFORM) {
            logLevel = PKILogger.Level.INFO;

        } else {
            logLevel = PKILogger.Level.WARN;
        }

        PKILogger.setLevel(logLevel);
    }

    /*  ISubsystem methods: */

    public static String ID = "debug";
    private static IConfigStore mConfig = null;

    public String getId() {
        return ID;
    }

    public void setId(String id) {
        ID = id;
    }

    private static final String PROP_ENABLED = "enabled";
    private static final String PROP_FILENAME = "filename";
    private static final String PROP_HASHKEYS = "hashkeytypes";
    private static final String PROP_LEVEL = "level";

    /**
     * Debug subsystem initialization. This subsystem is usually
     * given the following parameters:
     *
     * <pre>
     * debug.enabled   : (true|false) default false
     * debug.filename  : can be a pathname, or STDOUT
     * debug.hashkeytypes: comma-separated list of hashkey types
     *    possible values:  "CS.cfg"
     * debug.showcaller: (true|false) default false  [show caller method name for Debug.trace()]
     * </pre>
     */
    public void init(ISubsystem owner, IConfigStore config) {
        mConfig = config;
        String filename = null;
        String hashkeytypes = null;

        try {
            TRACE_ON = mConfig.getBoolean(PROP_ENABLED, false);
            if (TRACE_ON) {
                filename = mConfig.getString(PROP_FILENAME, null);
                if (filename == null) {
                    TRACE_ON = false;
                }
                hashkeytypes = mConfig.getString(PROP_HASHKEYS, null);
            }
            if (TRACE_ON) {
                if (hashkeytypes != null) {
                    StringTokenizer st = new StringTokenizer(hashkeytypes,
                            ",", false);
                    mHK = new Hashtable<String, String>();
                    while (st.hasMoreElements()) {
                        String hkr = st.nextToken();
                        mHK.put(hkr, "true");
                    }
                }
            }

            int level = mConfig.getInteger(PROP_LEVEL, VERBOSE);
            setLevel(level);

            CMS.logger.debug("============================================");
            CMS.logger.debug("=====  DEBUG SUBSYSTEM INITIALIZED   =======");
            CMS.logger.debug("============================================");

        } catch (Exception e) {
            // Don't do anything. Logging is not set up yet, and
            // we can't write to STDOUT.
        }
    }

    public void startup() {
    }

    public void shutdown() {
    }

    public IConfigStore getConfigStore() {
        return mConfig;
    }

    // for singleton

    public static Debug getInstance() {
        return mInstance;
    }

}
