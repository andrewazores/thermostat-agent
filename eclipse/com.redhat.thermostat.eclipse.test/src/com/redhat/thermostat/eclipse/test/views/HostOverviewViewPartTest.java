/*
 * Copyright 2012 Red Hat, Inc.
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

package com.redhat.thermostat.eclipse.test.views;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.widgets.Composite;
import org.junit.Test;
import org.mockito.InOrder;

import com.redhat.thermostat.client.core.views.BasicView;
import com.redhat.thermostat.client.core.views.HostOverviewViewProvider;
import com.redhat.thermostat.client.ui.HostOverviewController;
import com.redhat.thermostat.common.dao.HostInfoDAO;
import com.redhat.thermostat.common.dao.HostRef;
import com.redhat.thermostat.common.dao.NetworkInterfaceInfoDAO;
import com.redhat.thermostat.eclipse.ThermostatConstants;
import com.redhat.thermostat.eclipse.internal.views.HostOverviewViewPart;
import com.redhat.thermostat.eclipse.internal.views.RefViewPart;
import com.redhat.thermostat.eclipse.internal.views.SWTHostOverviewView;
import com.redhat.thermostat.eclipse.internal.views.SWTHostOverviewViewProvider;

public class HostOverviewViewPartTest extends AbstractRefViewPartTest<HostRef> {

    private HostOverviewController controller;
    private SWTHostOverviewViewProvider viewProvider;
    
    @Test
    public void testSelectionAfter() throws Exception {
        view.createPartControl(parent);

        HostRef hostRef = new HostRef("TEST", "Test");
        IStructuredSelection selection = mockSelection(hostRef);
        view.selectionChanged(hostVMView, selection);

        verifyViewProvider();
    }

    private void verifyViewProvider() {
        InOrder order = inOrder(viewProvider, controller);
        order.verify(viewProvider).setParent(any(Composite.class));
        order.verify(controller).getView();
    }

    @Override
    protected void mockController() {
        controller = mock(HostOverviewController.class);
        thermoView = mock(SWTHostOverviewView.class);

        HostInfoDAO hostInfoDao = mock(HostInfoDAO.class);
        NetworkInterfaceInfoDAO netIfaceDao = mock(NetworkInterfaceInfoDAO.class);
        viewProvider = mock(SWTHostOverviewViewProvider.class);
        when(osgi.getService(HostInfoDAO.class)).thenReturn(hostInfoDao);
        when(osgi.getService(NetworkInterfaceInfoDAO.class)).thenReturn(
                netIfaceDao);
        when(osgi.getService(HostOverviewViewProvider.class)).thenReturn(
                viewProvider);

        doReturn(controller).when(((HostOverviewViewPart) view))
                .createController(any(HostRef.class), same(hostInfoDao),
                        same(netIfaceDao), same(viewProvider));
        when(controller.getView()).thenReturn((BasicView) thermoView);
    }

    @Override
    protected RefViewPart<HostRef> createViewPart() {
        return new HostOverviewViewPart();
    }

    @Override
    protected String getViewID() {
        return ThermostatConstants.VIEW_ID_HOST_OVERVIEW;
    }

}