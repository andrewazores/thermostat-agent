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

package com.redhat.thermostat.vm.jmx.common.internal;

import java.util.HashSet;
import java.util.Set;

import com.redhat.thermostat.storage.core.PreparedParameter;
import com.redhat.thermostat.storage.core.auth.DescriptorMetadata;
import com.redhat.thermostat.storage.core.auth.StatementDescriptorRegistration;

/**
 * Registers prepared queries issued by this maven module
 * via {@link JmxNotificationDAOImpl}.
 *
 */
public class JmxNotificationDAOImplStatementDescriptorRegistration implements
        StatementDescriptorRegistration {
    
    private final Set<String> descs;
    
    public JmxNotificationDAOImplStatementDescriptorRegistration() {
        descs = new HashSet<>(2);
        descs.add(JmxNotificationDAOImpl.QUERY_LATEST_NOTIFICATION_STATUS);
        descs.add(JmxNotificationDAOImpl.QUERY_NOTIFICATIONS);
        descs.add(JmxNotificationDAOImpl.DESC_ADD_NOTIFICATION);
        descs.add(JmxNotificationDAOImpl.DESC_ADD_NOTIFICATION_STATUS);
    }

    @Override
    public Set<String> getStatementDescriptors() {
        return descs;
    }

    @Override
    public DescriptorMetadata getDescriptorMetadata(String descriptor,
            PreparedParameter[] params) {
        if (descs.contains(descriptor)) {
            // both queries we know about have agent/vmId parameters
            String agentId = (String)params[0].getValue();
            String vmId = (String)params[1].getValue();
            DescriptorMetadata metadata = new DescriptorMetadata(agentId, vmId);
            return metadata;
        } else {
            throw new IllegalArgumentException("Unknown descriptor: ->"
                    + descriptor + "<-");
        }
    }

}
