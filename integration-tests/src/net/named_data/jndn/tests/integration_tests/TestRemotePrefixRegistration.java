/**
 * Copyright (C) 2015 Regents of the University of California.
 *
 * @author: Jeff Thompson <jefft0@remap.ucla.edu>
 *
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>. A copy
 * of the GNU Lesser General Public License is in the file COPYING.
 */
package net.named_data.jndn.tests.integration_tests;

import java.util.logging.Logger;
import java.io.IOException;
import java.security.Key;
import net.named_data.jndn.Data;
import net.named_data.jndn.Face;
import net.named_data.jndn.Interest;
import net.named_data.jndn.Name;
import net.named_data.jndn.OnData;
import net.named_data.jndn.OnInterest;
import net.named_data.jndn.OnRegisterFailed;
import net.named_data.jndn.OnTimeout;
import net.named_data.jndn.security.KeyChain;
import net.named_data.jndn.transport.Transport;
import net.named_data.jndn.util.Blob;

/**
 * Common static methods and package classes for integration tests.
 */
public class TestRemotePrefixRegistration {

	private static Logger logger = Logger.getLogger(TestRemotePrefixRegistration.class.getName());

	public static void main(String[] args) throws Exception {
		Face face = new Face("192.168.1.8");
		KeyChain keyChain = new KeyChain();
		face.setCommandSigningInfo(keyChain, keyChain.getDefaultCertificateName());

		// test connection
		Interest interest = new Interest(new Name("/localhop/nfd/rib/list"));
		interest.setInterestLifetimeMilliseconds(1000);
		face.expressInterest(interest, new OnData() {
			@Override
			public void onData(Interest interest, Data data) {
				logger.info("Data received (bytes): " + data.getContent().size());
			}
		}, new OnTimeout() {
			@Override
			public void onTimeout(Interest interest) {
				logger.severe("Failed to retrieve localhop data from NFD: " + interest.toUri());
				System.exit(1);
			}
		});

		// register remotely
		face.registerPrefix(new Name("/remote-prefix"), new OnInterest() {
			@Override
			public void onInterest(Name prefix, Interest interest, Transport transport, long registeredPrefixId) {
				Data data = new Data(interest.getName());
				data.setContent(new Blob("..."));
				try {
					transport.send(data.wireEncode().buf());
				} catch (IOException e) {
					logger.severe("Failed to send data: " + e.getMessage());
					System.exit(1);
				}
			}
		}, new OnRegisterFailed() {
			@Override
			public void onRegisterFailed(Name prefix) {
				logger.severe("Failed to register the external forwarder: " + prefix.toUri());
				System.exit(1);
			}
		});

		// process events until 
		while (true) {
			face.processEvents();
		}
	}
}
