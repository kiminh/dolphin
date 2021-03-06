/*
 * Copyright (C) 2015 Seoul National University
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.snu.dolphin.ps.driver.impl;

import edu.snu.dolphin.ps.driver.api.ParameterServerManager;
import edu.snu.dolphin.ps.ns.EndpointId;
import edu.snu.dolphin.ps.ns.PSMessageHandler;
import edu.snu.dolphin.ps.server.concurrent.api.ParameterServer;
import edu.snu.dolphin.ps.server.concurrent.impl.ServerSideMsgHandler;
import edu.snu.dolphin.ps.server.concurrent.impl.ConcurrentParameterServer;
import edu.snu.dolphin.ps.worker.AsyncWorkerHandler;
import edu.snu.dolphin.ps.worker.api.ParameterWorker;
import edu.snu.dolphin.ps.worker.concurrent.ConcurrentParameterWorker;
import edu.snu.dolphin.ps.worker.concurrent.ConcurrentWorkerHandler;
import org.apache.reef.annotations.audience.DriverSide;
import org.apache.reef.driver.context.ServiceConfiguration;
import org.apache.reef.tang.Configuration;
import org.apache.reef.tang.Tang;

import javax.inject.Inject;
import java.util.concurrent.atomic.AtomicInteger;

import static edu.snu.dolphin.ps.common.Constants.SERVER_ID_PREFIX;
import static edu.snu.dolphin.ps.common.Constants.WORKER_ID_PREFIX;

/**
 * Manager class for a Parameter Server that uses only one node for a server.
 * This manager does NOT handle server or worker faults.
 */
@DriverSide
public final class ConcurrentParameterServerManager implements ParameterServerManager {
  private final AtomicInteger numWorkers;

  @Inject
  private ConcurrentParameterServerManager() {
    this.numWorkers = new AtomicInteger(0);
  }

  /**
   * Returns worker-side service configuration.
   * Sets {@link ConcurrentParameterWorker} as the {@link ParameterWorker} class.
   */
  @Override
  public Configuration getWorkerServiceConfiguration() {
    final int workerIndex = numWorkers.getAndIncrement();

    return Tang.Factory.getTang()
        .newConfigurationBuilder(ServiceConfiguration.CONF
            .set(ServiceConfiguration.SERVICES, ConcurrentParameterWorker.class)
            .build())
        .bindImplementation(ParameterWorker.class, ConcurrentParameterWorker.class)
        .bindImplementation(AsyncWorkerHandler.class, ConcurrentWorkerHandler.class)
        .bindNamedParameter(ServerId.class, SERVER_ID_PREFIX + 0)
        .bindNamedParameter(EndpointId.class, WORKER_ID_PREFIX + workerIndex)
        .build();
  }

  /**
   * Returns server-side service configuration.
   * Sets {@link ConcurrentParameterServer} as the {@link ParameterServer} class.
   */
  @Override
  public Configuration getServerServiceConfiguration() {
    return Tang.Factory.getTang()
        .newConfigurationBuilder(ServiceConfiguration.CONF
            .set(ServiceConfiguration.SERVICES, ConcurrentParameterServer.class)
            .build())
        .bindNamedParameter(PSMessageHandler.class, ServerSideMsgHandler.class)
        .bindImplementation(ParameterServer.class, ConcurrentParameterServer.class)
        .bindNamedParameter(EndpointId.class, SERVER_ID_PREFIX + 0)
        .build();
  }

}
