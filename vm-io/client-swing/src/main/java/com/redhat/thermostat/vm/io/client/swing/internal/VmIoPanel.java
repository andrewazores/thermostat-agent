/*
 * Copyright 2012-2015 Red Hat, Inc.
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

package com.redhat.thermostat.vm.io.client.swing.internal;

import java.awt.Component;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.swing.SwingUtilities;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.data.time.FixedMillisecond;
import org.jfree.data.time.RegularTimePeriod;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

import com.redhat.thermostat.client.core.experimental.Duration;
import com.redhat.thermostat.client.swing.SwingComponent;
import com.redhat.thermostat.client.swing.components.HeaderPanel;
import com.redhat.thermostat.client.swing.components.experimental.SingleValueChartPanel;
import com.redhat.thermostat.client.swing.experimental.ComponentVisibilityNotifier;
import com.redhat.thermostat.common.ActionListener;
import com.redhat.thermostat.common.ActionNotifier;
import com.redhat.thermostat.common.model.Range;
import com.redhat.thermostat.shared.locale.Translate;
import com.redhat.thermostat.storage.model.DiscreteTimeData;
import com.redhat.thermostat.vm.io.client.core.LocaleResources;
import com.redhat.thermostat.vm.io.client.core.VmIoView;

public class VmIoPanel extends VmIoView implements SwingComponent {

    private static final Translate<LocaleResources> translator = LocaleResources.createLocalizer();

    private static final int DEFAULT_DURATION_VALUE = 10;
    private static final TimeUnit DEFAULT_DURATION_UNIT = TimeUnit.MINUTES;

    private Duration duration;

    private HeaderPanel visiblePanel;

    private final TimeSeriesCollection data = new TimeSeriesCollection();
    private final TimeSeries ioTimeSeries = new TimeSeries("io-stats");

    private SingleValueChartPanel chartPanel;

    private ActionNotifier<UserAction> userActionNotifier = new ActionNotifier<UserAction>(this);

    public VmIoPanel() {
        super();
        data.addSeries(ioTimeSeries);

        duration = new Duration(DEFAULT_DURATION_VALUE, DEFAULT_DURATION_UNIT);

        initializePanel();

        new ComponentVisibilityNotifier().initialize(visiblePanel, notifier);
    }

    @Override
    public Component getUiComponent() {
        return visiblePanel;
    }

    private void initializePanel() {
        visiblePanel = new HeaderPanel();
        visiblePanel.setHeader(translator.localize(LocaleResources.VM_IO_TITLE));

        JFreeChart chart = ChartFactory.createTimeSeriesChart(
                null,
                translator.localize(LocaleResources.VM_IO_CHART_TIME_LABEL).getContents(),
                translator.localize(LocaleResources.VM_IO_CHART_CHARACTERS_READ_LABEL).getContents(),
                data,
                false, false, false);

        chart.getXYPlot().getRangeAxis().setLowerBound(0.0);

        chartPanel = new SingleValueChartPanel(chart, duration);

        visiblePanel.setContent(chartPanel);

        chartPanel.addPropertyChangeListener(SingleValueChartPanel.PROPERTY_VISIBLE_TIME_RANGE, new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                duration = (Duration) evt.getNewValue();
                userActionNotifier.fireAction(UserAction.USER_CHANGED_TIME_RANGE);
            }
        });
    }

    @Override
    public void addUserActionListener(ActionListener<UserAction> listener) {
        userActionNotifier.addActionListener(listener);
    }

    @Override
    public void removeUserActionListener(ActionListener<UserAction> listener) {
        userActionNotifier.removeActionListener(listener);
    }

    @Override
    public void setAvailableDataRange(Range<Long> availableInterval) {
        // FIXME indicate the total data range to the user somehow
    }

    @Override
    public void setVisibleDataRange(int time, TimeUnit unit) {
        chartPanel.setTimeRangeToShow(time, unit);
    }

    @Override
    public Duration getUserDesiredDuration() {
        return duration;
    }

    @Override
    public void addData(List<DiscreteTimeData<? extends Number>> data) {
        final List<DiscreteTimeData<? extends Number>> copy = new ArrayList<>(data);
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                for (DiscreteTimeData<? extends Number> data: copy) {
                    RegularTimePeriod period = new FixedMillisecond(data.getTimeInMillis());
                    if (ioTimeSeries.getDataItem(period) == null) {
                        ioTimeSeries.add(period, data.getData(), false);
                    }
                }
                ioTimeSeries.fireSeriesChanged();
            }
        });
    }

    @Override
    public void clearData() {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                ioTimeSeries.clear();
            }
        });
    }
}
