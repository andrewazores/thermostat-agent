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

package com.redhat.thermostat.host.overview.model;

import com.redhat.thermostat.storage.core.Entity;
import com.redhat.thermostat.storage.core.Persist;
import com.redhat.thermostat.storage.model.BasePojo;

@Entity
public class HostInfo extends BasePojo {

    private String hostname;
    private String osName;
    private String osKernel;
    private String cpuModel;
    private int cpuCount;
    private long totalMemory;

    public HostInfo() {
        this(null, null, null, null, null, -1, -1);
    }

    public HostInfo(String writerId, String hostname, String osName, String osKernel, String cpuModel, int cpuCount, long totalMemory) {
        super(writerId);
        this.hostname = hostname;
        this.osName = osName;
        this.osKernel = osKernel;
        this.cpuModel = cpuModel;
        this.cpuCount = cpuCount;
        this.totalMemory = totalMemory;
    }

    @Persist
    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    @Persist
    public void setOsName(String osName) {
        this.osName = osName;
    }

    @Persist
    public void setOsKernel(String osKernel) {
        this.osKernel = osKernel;
    }

    @Persist
    public void setCpuModel(String cpuModel) {
        this.cpuModel = cpuModel;
    }

    @Persist
    public void setCpuCount(int cpuCount) {
        this.cpuCount = cpuCount;
    }

    @Persist
    public void setTotalMemory(long totalMemory) {
        this.totalMemory = totalMemory;
    }

    @Persist
    public String getHostname() {
        return hostname;
    }

    @Persist
    public String getOsName() {
        return osName;
    }

    @Persist
    public String getOsKernel() {
        return osKernel;
    }

    @Persist
    public String getCpuModel() {
        return cpuModel;
    }

    @Persist
    public int getCpuCount() {
        return cpuCount;
    }

    /**
     * Total memory in bytes
     */
    @Persist
    public long getTotalMemory() {
        return totalMemory;
    }

}

