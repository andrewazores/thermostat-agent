/*
 * Copyright 2012, 2013 Red Hat, Inc.
 *
 * This file is part of Thermostat.
 *
 * Thermostat is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published
 * by the Free Software Foundation; either version 2, or (at your
 * option) any later version.
 *
 * Thermostat is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Thermostat; see the file COPYING.  If not see
 * <http://www.gnu.org/licenses/>.
 *
 * Linking this code with other modules is making a combined work
 * based on this code.  Thus, the terms and conditions of the GNU
 * General Public License cover the whole combination.
 *
 * As a special exception, the copyright holders of this code give
 * you permission to link this code with independent modules to
 * produce an executable, regardless of the license terms of these
 * independent modules, and to copy and distribute the resulting
 * executable under terms of your choice, provided that you also
 * meet, for each linked independent module, the terms and conditions
 * of the license of that module.  An independent module is a module
 * which is not derived from or based on this code.  If you modify
 * this code, you may extend this exception to your version of the
 * library, but you are not obligated to do so.  If you do not wish
 * to do so, delete this exception statement from your version.
 */

package com.redhat.thermostat.utils.management.internal;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.rmi.NoSuchObjectException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.rmi.server.RMISocketFactory;
import java.rmi.server.UnicastRemoteObject;
import java.util.logging.Logger;

import com.redhat.thermostat.common.utils.LoggingUtils;

public class RMIRegistry {
    
    private static final Logger logger = LoggingUtils.getLogger(RMIRegistry.class);

    private RegistryCreator registryCreator;
    private ServerSocketCreator serverSockCreator;
    private Registry registry;
    
    public RMIRegistry() {
        this(new RegistryCreator(), new ServerSocketCreator());
    }
    
    RMIRegistry(RegistryCreator registryCreator, ServerSocketCreator serverSockCreator) {
        this.registryCreator = registryCreator;
        this.serverSockCreator = serverSockCreator;
    }
    
    public void start() throws RemoteException {
        this.registry = registryCreator.createRegistry(Registry.REGISTRY_PORT /* TODO customize */,
                RMISocketFactory.getDefaultSocketFactory(),
                new RMIServerSocketFactory() {
                    
                    @Override
                    public ServerSocket createServerSocket(int port) throws IOException {
                        // Allow only local connections
                        return serverSockCreator.createSocket(port, 0, InetAddress.getLoopbackAddress());
                    }
                });
        logger.fine("Starting RMI registry");
    }
    
    public Registry getRegistry() throws RemoteException {
        // We get a class loading problem when returning the local registry reference,
        // this returns a remote stub reference instead
        return registryCreator.getRegistry();
    }
    
    public void stop() throws RemoteException {
        if (registry != null) {
            registryCreator.destroyRegistry(registry);
            registry = null;
            logger.fine("Shutting down RMI registry");
        }
    }
    
    public Remote export(Remote obj) throws RemoteException {
        if (registry == null) {
            throw new RemoteException("RMI registry is not running");
        }
        return UnicastRemoteObject.exportObject(obj, 0);
    }
    
    public void unexport(Remote obj) throws RemoteException {
        if (registry == null) {
            throw new RemoteException("RMI registry is not running");
        }
        UnicastRemoteObject.unexportObject(obj, true);
    }
    
    /*
     * For testing purposes only.
     */
    Registry getRegistryImpl() {
        return registry;
    }
    
    static class RegistryCreator {
        Registry createRegistry(int port, RMIClientSocketFactory csf,
                RMIServerSocketFactory ssf) throws RemoteException {
            return LocateRegistry.createRegistry(port, csf, ssf);
        }
        
        Registry getRegistry() throws RemoteException {
            return LocateRegistry.getRegistry(InetAddress.getLoopbackAddress().getHostName());
        }
        
        void destroyRegistry(Registry registry) throws NoSuchObjectException {
            // Shuts down RMI registry
            UnicastRemoteObject.unexportObject(registry, true);
        }
    }
    
    static class ServerSocketCreator {
        ServerSocket createSocket(int port, int backlog, InetAddress bindAddr) throws IOException {
            return new ServerSocket(port, backlog, bindAddr);
        }
    }

}
