/*
 * Copyright 2009 Joseph Fifield
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.programmerplanet.sshtunnel.model;

import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.swt.widgets.Shell;
import org.programmerplanet.sshtunnel.ui.DefaultUserInfo;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.UserInfo;

/**
 * Responsible for connecting and disconnecting ssh connections and the
 * underlying tunnels.
 * 
 * @author <a href="jfifield@programmerplanet.org">Joseph Fifield</a>
 */
public class ConnectionManager {

	private static final Log log = LogFactory.getLog(ConnectionManager.class);

	private static final int TIMEOUT = 30000;

	private static final ConnectionManager INSTANCE = new ConnectionManager();

	public static ConnectionManager getInstance() {
		return INSTANCE;
	}

	private Map<Session, com.jcraft.jsch.Session> connections = new HashMap<Session, com.jcraft.jsch.Session>();

	public void connect(Session session, Shell parent) throws ConnectionException {
		log.info("Connecting session: " + session);
		clearTunnelExceptions(session);
		com.jcraft.jsch.Session jschSession = connections.get(session);
		try {
			if (jschSession == null) {
				JSch jsch = new JSch();
				File knownHosts = getKnownHostsFile();
				jsch.setKnownHosts(knownHosts.getAbsolutePath());
				jschSession = jsch.getSession(session.getUsername(), session.getHostname(), session.getPort());
			}
			UserInfo userInfo = null;
			if (session.getPassword() != null && session.getPassword().trim().length() > 0) {
				userInfo = new DefaultUserInfo(parent, session.getPassword());
			} else {
				userInfo = new DefaultUserInfo(parent);
			}
			jschSession.setUserInfo(userInfo);
			jschSession.connect(TIMEOUT);

			startTunnels(session, jschSession);
		} catch (JSchException e) {
			jschSession.disconnect();
			jschSession = null;
			throw new ConnectionException(e);
		}
		connections.put(session, jschSession);
	}

	private File getKnownHostsFile() {
		String userHome = System.getProperty("user.home");
		File f = new File(userHome);
		f = new File(f, ".ssh");
		f = new File(f, "known_hosts");
		return f;
	}

	private void startTunnels(Session session, com.jcraft.jsch.Session jschSession) {
		for (Iterator i = session.getTunnels().iterator(); i.hasNext();) {
			Tunnel tunnel = (Tunnel) i.next();
			try {
				startTunnel(jschSession, tunnel);
			} catch (Exception e) {
				tunnel.setException(e);
				log.error("Error starting tunnel: " + tunnel, e);
			}
		}
	}

	private void startTunnel(com.jcraft.jsch.Session jschSession, Tunnel tunnel) throws JSchException {
		if (tunnel.getLocal()) {
			jschSession.setPortForwardingL(tunnel.getLocalAddress(), tunnel.getLocalPort(), tunnel.getRemoteAddress(), tunnel.getRemotePort());
		} else {
			jschSession.setPortForwardingR(tunnel.getRemoteAddress(), tunnel.getRemotePort(), tunnel.getLocalAddress(), tunnel.getLocalPort());
		}
	}

	public void disconnect(Session session) {
		log.info("Disconnecting session: " + session);
		clearTunnelExceptions(session);
		com.jcraft.jsch.Session jschSession = connections.get(session);
		if (jschSession != null) {
			stopTunnels(session, jschSession);
			jschSession.disconnect();
		}
		connections.remove(session);
	}

	private void stopTunnels(Session session, com.jcraft.jsch.Session jschSession) {
		for (Iterator i = session.getTunnels().iterator(); i.hasNext();) {
			Tunnel tunnel = (Tunnel) i.next();
			try {
				stopTunnel(jschSession, tunnel);
			} catch (Exception e) {
				log.error("Error stopping tunnel: " + tunnel, e);
			}
		}
	}

	private void stopTunnel(com.jcraft.jsch.Session jschSession, Tunnel tunnel) throws JSchException {
		if (tunnel.getLocal()) {
			jschSession.delPortForwardingL(tunnel.getLocalAddress(), tunnel.getLocalPort());
		} else {
			jschSession.delPortForwardingR(tunnel.getRemotePort());
		}
	}

	private void clearTunnelExceptions(Session session) {
		for (Iterator i = session.getTunnels().iterator(); i.hasNext();) {
			Tunnel tunnel = (Tunnel) i.next();
			tunnel.setException(null);
		}
	}

	public boolean isConnected(Session session) {
		com.jcraft.jsch.Session jschSession = connections.get(session);
		return jschSession != null && jschSession.isConnected();
	}

}
