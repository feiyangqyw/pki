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
package com.netscape.cmscore.ldapconn;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Iterator;
import java.util.Vector;

import org.dogtagpki.server.PKIClientSocketListener;
import org.mozilla.jss.ssl.SSLClientCertificateSelectionCallback;
import org.mozilla.jss.ssl.SSLHandshakeCompletedEvent;
import org.mozilla.jss.ssl.SSLHandshakeCompletedListener;
import org.mozilla.jss.ssl.SSLSocket;

import com.netscape.certsrv.base.IConfigStore;
import com.netscape.cmscore.apps.CMS;
import com.netscape.cmscore.apps.CMSEngine;

import netscape.ldap.LDAPException;
import netscape.ldap.LDAPSSLSocketFactoryExt;

/**
 * Uses HCL ssl socket.
 *
 * @author Lily Hsiao lhsiao@netscape.com
 */
public class PKISocketFactory implements LDAPSSLSocketFactoryExt {

    public static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(PKISocketFactory.class);

    private boolean secure;
    private String mClientAuthCertNickname;
    private boolean mClientAuth;
    private boolean keepAlive;
    PKIClientSocketListener sockListener = null;

    public PKISocketFactory() {
        init();
    }

    public PKISocketFactory(boolean secure) {
        this.secure = secure;
        init();
    }

    public PKISocketFactory(String certNickname) {
        this.secure = true;
        mClientAuthCertNickname = certNickname;
        init();
    }

    public void init() {
        CMSEngine engine = (CMSEngine) CMS.getCMSEngine();
        try {
            IConfigStore cs = engine.getConfigStore();
            keepAlive = cs.getBoolean("tcp.keepAlive", true);
            logger.debug("TCP Keep-Alive: " + keepAlive);
            sockListener = new PKIClientSocketListener();

        } catch (Exception e) {
            String message = "Unable to read TCP configuration: " + e;
            logger.error(message, e);
            throw new RuntimeException(message, e);
        }
    }

    public SSLSocket makeSSLSocket(String host, int port) throws UnknownHostException, IOException {
        String method = "ldapconn/PKISocketFactory.makeSSLSocket: ";
        logger.debug(method + "begins");

        /*
         * let inherit TLS range and cipher settings
         */

        SSLSocket s;

        if (mClientAuthCertNickname == null) {
            s = new SSLSocket(host, port);

        } else {
            // Let's create a selection callback in the case the client auth
            // No longer manually set the cert name.
            // This two step process, used in the JSS client auth test suite,
            // appears to be needed to get this working.

            Socket js = new Socket(InetAddress.getByName(host), port);
            s = new SSLSocket(js, host,
                    null,
                    new SSLClientCertificateSelectionCB(mClientAuthCertNickname));
        }

        s.setUseClientMode(true);
        s.enableV2CompatibleHello(false);

        s.addSocketListener(sockListener);

        SSLHandshakeCompletedListener listener = null;

        listener = new ClientHandshakeCB(this);
        s.addHandshakeCompletedListener(listener);

        if (mClientAuthCertNickname != null) {
            mClientAuth = true;
            logger.debug("LdapJssSSLSocket: set client auth cert nickname " +
                    mClientAuthCertNickname);

            //We have already established the manual cert selection callback
            //Doing it this way will provide some debugging info on the candidate certs
        }
        s.forceHandshake();

        return s;
    }

    public Socket makeSocket(String host, int port) throws LDAPException {
        Socket s = null;

        try {
            if (!secure) {
                s = new Socket(host, port);

            } else {
                s = makeSSLSocket(host, port);
            }

            s.setKeepAlive(keepAlive);

        } catch (Exception e) {
            String message = "Unable to create socket: " + e;
            logger.error(message, e);
            if (s != null) {
                try {
                    s.close();
                } catch (IOException e1) {
                    logger.warn("Unable to close socket: " + e1.getMessage(), e1);
                }
            }
            throw new LDAPException(message);
        }

        return s;
    }

    public boolean isClientAuth() {
        return mClientAuth;
    }

    public Object getCipherSuites() {
        return null;
    }

    public void log(int level, String msg) {
    }

    static class ClientHandshakeCB implements SSLHandshakeCompletedListener {
        Object sc;

        public ClientHandshakeCB(Object sc) {
            this.sc = sc;
        }

        public void handshakeCompleted(SSLHandshakeCompletedEvent event) {
            logger.debug("SSL handshake happened");
        }
    }

    static class SSLClientCertificateSelectionCB implements SSLClientCertificateSelectionCallback {
        String desiredCertName = null;

        public SSLClientCertificateSelectionCB(String clientAuthCertNickname) {
            logger.debug("SSLClientCertificateSelectionCB: Setting desired cert nickname to: " + clientAuthCertNickname);
            desiredCertName = clientAuthCertNickname;
        }

        @Override
        public String select(Vector<String> certs) {

            logger.debug("SSLClientCertificatSelectionCB: Entering!");

            if(desiredCertName == null) {
                return null;
            }

            @SuppressWarnings("unchecked")
            Iterator<String> itr = certs.iterator();
            String selection = null;

            while(itr.hasNext()){
                String candidate = itr.next();
                logger.debug("Candidate cert: " + candidate);
                if(desiredCertName.equalsIgnoreCase(candidate)) {
                    selection = candidate;
                    logger.debug("SSLClientCertificateSelectionCB: desired cert found in list: " + desiredCertName);
                    break;
                }
            }

            logger.debug("SSLClientCertificateSelectionCB: returning: " + selection);
            return selection;

        }

    }

}
