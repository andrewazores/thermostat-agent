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

package com.redhat.thermostat.common.portability.internal.linux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.UUID;

import com.redhat.thermostat.common.portability.internal.linux.DistributionInformation;
import com.redhat.thermostat.common.portability.internal.linux.LsbRelease;
import com.redhat.thermostat.shared.config.OS;
import org.junit.Assume;
import org.junit.Test;

public class LsbReleaseTest {
    
    static final String NOT_EXISTING_LSB_RELEASE = "lsb_release-"
            + UUID.randomUUID();

    @Test
    public void testName() throws IOException, InterruptedException {
        Assume.assumeTrue(OS.IS_LINUX);
        BufferedReader reader = new BufferedReader(new StringReader("Distributor ID: Name"));
        DistributionInformation info = new LsbRelease().getFromLsbRelease(reader);
        assertEquals("Name", info.getName());
    }

    @Test
    public void testVersion() throws IOException {
        Assume.assumeTrue(OS.IS_LINUX);
        BufferedReader reader = new BufferedReader(new StringReader("Release: Version"));
        DistributionInformation info = new LsbRelease().getFromLsbRelease(reader);
        assertEquals("Version", info.getVersion());
    }
    
    @Test
    public void getDistributionInformationThrowsIOExceptionIfScriptNotThere() {
        Assume.assumeTrue(OS.IS_LINUX);
        LsbRelease lsbRelease = new LsbRelease(NOT_EXISTING_LSB_RELEASE);
        try {
            lsbRelease.getDistributionInformation();
            fail("Should have thrown IOException, since file is not there!");
        } catch (IOException e) {
            // pass
            String message = e.getMessage();
            assertTrue(message.contains("Cannot run program \"lsb_release-"));
            assertTrue(message.contains("No such file or directory"));
        }
    }

}

