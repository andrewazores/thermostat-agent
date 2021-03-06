/*
 * Copyright 2012-2017 Red Hat, Inc.
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

package com.redhat.thermostat.agent.ipc.tcpsocket.server.internal;

import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.redhat.thermostat.agent.ipc.server.ThermostatIPCCallbacks;
import com.redhat.thermostat.common.utils.LoggingUtils;

class AcceptThread extends Thread {
    
    private static final Logger logger = LoggingUtils.getLogger(AcceptThread.class);
    private final ExecutorService execService;
    private final Selector selector;
    private final ClientHandlerCreator handlerCreator;
    private final Object registerLock = new Object();
    
    private boolean shutdown;
    
    AcceptThread(Selector selector, ExecutorService execService) {
        this(selector, execService, new ClientHandlerCreator());
    }
    
    AcceptThread(Selector selector, ExecutorService execService, ClientHandlerCreator handlerCreator) {
        this.selector = selector;
        this.execService = execService;
        this.handlerCreator = handlerCreator;
        this.shutdown = false;
    }
    
    @Override
    public void run() {
        logger.info("Ready to accept client connections");
        try {
            while (!shutdown) {
                synchronized(registerLock) {}
                int selected = selector.select();
                if (selected < 0) {
                    // Something bad happened
                    throw new IOException("Error occurred while selecting channel");
                } else if (selected > 0) {
                    Set<SelectionKey> selectedKeys = selector.selectedKeys();
                    // Use a copy to prevent concurrent modification
                    Set<SelectionKey> selectedCopy = new HashSet<>(selectedKeys);
                    for (SelectionKey key : selectedCopy) {
                        // Remove this key from selected set to indicate we've processed it
                        selectedKeys.remove(key);
                        processKey(key);
                    }
                }
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error occurred during selection", e);
            shutdown = true;
        } finally {
            logger.info("Shutting down");
            execService.shutdown();
        }
    }

    private void processKey(SelectionKey key) {
        // Ensure this key is still valid and hasn't been cancelled (e.g. by closing the socket)
        if (key.readyOps() > 0 && key.isValid()) {
            logger.finest("Got selection operation: " + key.readyOps());
            try {
                if (key.isAcceptable()) {
                    // We stored our wrapper socket as the attachment
                    ThermostatServerSocketChannelImpl channel = 
                            safeGetAttachment(key, ThermostatServerSocketChannelImpl.class);
                    // Accept client connection
                    AcceptedSocketChannelImpl client = channel.accept();
                    // Create handler for accepted client with provided callbacks
                    ThermostatIPCCallbacks callbacks = channel.getCallbacks();
                    ClientHandler handler = handlerCreator.createHandler(client, execService, callbacks);
                    // Store handler as attachment
                    SelectionKey clientKey = client.getSelectionKey();
                    clientKey.attach(handler);
                    logger.fine("Accepted client for \"" + channel.getName() + "\"");
                } else { 
                    if (key.isReadable()) {
                        // Call handler for client to perform read
                        ClientHandler handler = safeGetAttachment(key, ClientHandler.class);
                        handler.handleRead();
                    }
                    // Check key hasn't been cancelled by a prior read operation
                    if (key.isValid() && key.isWritable()) {
                        // Call handler for client to perform write
                        ClientHandler handler = safeGetAttachment(key, ClientHandler.class);
                        handler.handleWrite();
                    }
                }
            } catch (IOException e) {
                logger.log(Level.WARNING, "Failed to process socket event", e);
            }
        }
    }

    /**
     * Register a new channel to this acceptThread.
     * Handles the select() loop and associated locks.
     *
     * See http://php.mandelson.org/mk3/index.php/2011/10/06/better-selectablechannel-registration-in-java-nio/
     * This could also be an issue for Unix Domain Sockets, but it doesn't seem to be a problem on Linux for
     * either UDS or TCP sockets on Linux - not sure why.
     *
     * May need to add a simiilar deregister() function for clean shutdown.
     *
     * @param channel
     * @param ops
     * @return
     * @throws IOException
     */
    SelectionKey register(SelectableChannel channel, int ops) throws IOException {
        final SelectionKey key;
        synchronized(registerLock) {
            selector.wakeup();
            key =  channel.register(selector, ops);
        }
        return key;
    }

    void shutdown() throws IOException {
        this.shutdown = true;
        // Interrupt accept thread
        this.interrupt();
        
        try {
            this.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    boolean isShutdown() {
        return shutdown;
    }
    
    private <T> T safeGetAttachment(SelectionKey key, Class<T> clazz) throws IOException {
        Object attachment = key.attachment();
        Objects.requireNonNull(attachment, "Expected attachment in SelectionKey");
        if (!clazz.isAssignableFrom(attachment.getClass())) {
            throw new IOException("Invalid SelectionKey");
        }
        return (T) clazz.cast(attachment);
    }
    
    static class ClientHandlerCreator {
        ClientHandler createHandler(AcceptedSocketChannelImpl client, ExecutorService execService, 
                ThermostatIPCCallbacks callbacks) {
            return new ClientHandler(client, execService, callbacks);
        }
    }
    
}