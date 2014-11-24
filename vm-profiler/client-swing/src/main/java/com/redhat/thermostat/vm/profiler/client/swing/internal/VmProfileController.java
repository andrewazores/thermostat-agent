/*
 * Copyright 2012-2014 Red Hat, Inc.
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

package com.redhat.thermostat.vm.profiler.client.swing.internal;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.redhat.thermostat.client.command.RequestQueue;
import com.redhat.thermostat.client.core.controllers.InformationServiceController;
import com.redhat.thermostat.client.core.views.BasicView;
import com.redhat.thermostat.client.core.views.BasicView.Action;
import com.redhat.thermostat.client.core.views.UIComponent;
import com.redhat.thermostat.common.ActionEvent;
import com.redhat.thermostat.common.ActionListener;
import com.redhat.thermostat.common.ApplicationService;
import com.redhat.thermostat.common.Clock;
import com.redhat.thermostat.common.SystemClock;
import com.redhat.thermostat.common.Timer;
import com.redhat.thermostat.common.Timer.SchedulingType;
import com.redhat.thermostat.common.command.Request;
import com.redhat.thermostat.common.model.Range;
import com.redhat.thermostat.shared.locale.LocalizedString;
import com.redhat.thermostat.shared.locale.Translate;
import com.redhat.thermostat.storage.core.VmRef;
import com.redhat.thermostat.storage.dao.AgentInfoDAO;
import com.redhat.thermostat.vm.profiler.client.core.ProfilingResultParser;
import com.redhat.thermostat.vm.profiler.client.core.ProfilingResultParser.ProfilingResult;
import com.redhat.thermostat.vm.profiler.client.swing.internal.VmProfileView.Profile;
import com.redhat.thermostat.vm.profiler.client.swing.internal.VmProfileView.ProfileAction;
import com.redhat.thermostat.vm.profiler.common.ProfileDAO;
import com.redhat.thermostat.vm.profiler.common.ProfileInfo;
import com.redhat.thermostat.vm.profiler.common.ProfileRequest;

public class VmProfileController implements InformationServiceController<VmRef> {

    private static final Translate<LocaleResources> translator = LocaleResources.createLocalizer();

    private ApplicationService service;
    private ProfileDAO profileDao;
    private AgentInfoDAO agentInfoDao;
    private RequestQueue queue;
    private VmRef vm;

    private VmProfileView view;

    private Timer updater;

    private Clock clock;

    public VmProfileController(ApplicationService service,
            AgentInfoDAO agentInfoDao, ProfileDAO dao,
            RequestQueue queue,
            VmRef vm) {
        this(service, agentInfoDao, dao, queue, new SystemClock(), new SwingVmProfileView(), vm);
    }

    VmProfileController(ApplicationService service,
            AgentInfoDAO agentInfoDao, ProfileDAO dao,
            RequestQueue queue, Clock clock,
            VmProfileView view, VmRef vm) {
        this.service = service;
        this.agentInfoDao = agentInfoDao;
        this.profileDao = dao;
        this.queue = queue;
        this.clock = clock;
        this.view = view;
        this.vm = vm;

        // TODO dispose the timer when done
        updater = service.getTimerFactory().createTimer();
        updater.setSchedulingType(SchedulingType.FIXED_DELAY);
        updater.setInitialDelay(0);
        updater.setDelay(5);
        updater.setTimeUnit(TimeUnit.SECONDS);
        updater.setAction(new Runnable() {
            @Override
            public void run() {
                updateViewWithProfiledRuns();
            }
        });

        view.addActionListener(new ActionListener<BasicView.Action>() {
            @Override
            public void actionPerformed(ActionEvent<Action> actionEvent) {
                switch (actionEvent.getActionId()) {
                    case HIDDEN:
                        updater.stop();
                        break;
                    case VISIBLE:
                        updater.start();
                        break;
                    default:
                        throw new AssertionError("Unknown action event: " + actionEvent);
                }
            }
        });

        view.addProfileActionListener(new ActionListener<VmProfileView.ProfileAction>() {
            @Override
            public void actionPerformed(ActionEvent<ProfileAction> actionEvent) {
                ProfileAction id = actionEvent.getActionId();
                switch (id) {
                case START_PROFILING:
                    sendProfilingRequest(true);
                    break;
                case STOP_PROFILING:
                    sendProfilingRequest(false);
                    break;
                case PROFILE_SELECTED:
                    updateViewWithProfileRunData();
                    break;
                default:
                    throw new AssertionError("Unknown event: " + id);
                }
            }
        });
    }

    private void sendProfilingRequest(boolean start) {
        InetSocketAddress address = agentInfoDao.getAgentInformation(vm.getHostRef()).getRequestQueueAddress();
        String action = start ? ProfileRequest.START_PROFILING : ProfileRequest.STOP_PROFILING;
        Request req = ProfileRequest.create(address, vm.getVmId(), action);
        queue.putRequest(req);
    }

    private void updateViewWithProfiledRuns() {
        long end = clock.getRealTimeMillis();
        long start = end - TimeUnit.DAYS.toMillis(1); // FIXME hardcoded 1 day

        List<ProfileInfo> profileInfos = profileDao.getAllProfileInfo(vm, new Range<>(start, end));
        List<Profile> profiles = new ArrayList<>();
        for (ProfileInfo profileInfo : profileInfos) {
            Profile profile = new Profile(profileInfo.getProfileId(), profileInfo.getTimeStamp());
            profiles.add(profile);
        }

        view.setAvailableProfilingRuns(profiles);
    }

    private void updateViewWithProfileRunData() {
        Profile selectedProfile = view.getSelectedProfile();
        String profileId = selectedProfile.name;
        InputStream in = profileDao.loadProfileDataById(vm, profileId);
        ProfilingResult result = new ProfilingResultParser().parse(in);
        view.setProfilingDetailData(result);
    }

    @Override
    public UIComponent getView() {
        return view;
    }

    @Override
    public LocalizedString getLocalizedName() {
        return translator.localize(LocaleResources.PROFILER_TAB_NAME);
    }

}