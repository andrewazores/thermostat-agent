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

package com.redhat.thermostat.storage.internal;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;

import com.redhat.thermostat.common.config.experimental.ConfigurationInfoSource;
import com.redhat.thermostat.shared.config.CommonPaths;
import com.redhat.thermostat.shared.config.InvalidConfigurationException;

public class StorageCoreConfiguration {

    private static final String CONFIG_FILE = "gateway.properties";
    private static final String URL_PROP = "gatewayUrl";

    private CommonPaths paths;

    public StorageCoreConfiguration(CommonPaths paths) {
        this.paths = paths;
    }

    public String getGatewayURL() throws IOException {
        Properties props = getProperties(paths.getGatewayPropertiesFile());
        String url = (String) props.get(URL_PROP);
        if (url == null) {
            throw new IOException("No Gateway URL found in " + getConfigFilePath());
        }
        return url;
    }

    private Properties getProperties(File filename) {
        Properties systemConfig = new Properties();
        if (filename != null) {
            try {
                systemConfig.load(new FileInputStream(filename));
            } catch (IOException e) {
                throw new InvalidConfigurationException(e);
            }
        }
        return systemConfig;
    }

    private String getConfigFilePath() {
        StringBuilder builder = new StringBuilder();
        builder.append("$THERMOSTAT_HOME").append(File.separator).append("etc").append(File.separator)
                .append(CONFIG_FILE);
        return builder.toString();
    }

}
