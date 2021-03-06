/*
 * Copyright (C) 2016 Seoul National University
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
package edu.snu.dolphin.ps.server;

import edu.snu.dolphin.ps.ParameterServerParameters;
import edu.snu.dolphin.ps.TestUtils;
import edu.snu.dolphin.ps.common.partitioned.resolver.ServerResolver;
import edu.snu.dolphin.ps.common.partitioned.resolver.SingleNodeServerResolver;
import edu.snu.dolphin.ps.driver.impl.ServerId;
import edu.snu.dolphin.ps.examples.add.IntegerCodec;
import edu.snu.dolphin.ps.ns.EndpointId;
import edu.snu.dolphin.ps.server.api.ParameterUpdater;
import edu.snu.dolphin.ps.server.partitioned.PartitionedParameterServer;
import edu.snu.dolphin.ps.server.partitioned.PartitionedServerSideReplySender;
import edu.snu.dolphin.ps.common.partitioned.parameters.NumPartitions;
import org.apache.reef.tang.Configuration;
import org.apache.reef.tang.Injector;
import org.apache.reef.tang.Tang;
import org.apache.reef.tang.exceptions.InjectionException;
import org.junit.Before;
import org.junit.Test;

import javax.inject.Inject;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static edu.snu.dolphin.ps.common.Constants.SERVER_ID_PREFIX;


/**
 * Tests for {@link PartitionedParameterServer}.
 */
public final class PartitionedParameterServerTest {
  private static final Integer KEY = 0;
  private static final String MSG_THREADS_NOT_FINISHED = "threads not finished (possible deadlock or infinite loop)";
  private static final String MSG_RESULT_ASSERTION = "final result of concurrent pushes and pulls";
  private PartitionedParameterServer<Integer, Integer, Integer> server;
  private MockPartitionedServerSideReplySender mockSender;

  @Before
  public void setup() throws InjectionException {
    final Configuration conf = Tang.Factory.getTang().newConfigurationBuilder()
        .bind(PartitionedServerSideReplySender.class, MockPartitionedServerSideReplySender.class)
        .bindImplementation(ServerResolver.class, SingleNodeServerResolver.class)
        .bindNamedParameter(ServerId.class, SERVER_ID_PREFIX + 0)
        .bindNamedParameter(EndpointId.class, SERVER_ID_PREFIX + 0)
        .bindNamedParameter(ParameterServerParameters.KeyCodecName.class, IntegerCodec.class)
        .bindNamedParameter(ParameterServerParameters.ValueCodecName.class, IntegerCodec.class)
        .bindNamedParameter(ParameterServerParameters.PreValueCodecName.class, IntegerCodec.class)
        .bindNamedParameter(NumPartitions.class, "4")
        .build();
    final Injector injector = Tang.Factory.getTang().newInjector(conf);
    injector.bindVolatileInstance(ParameterUpdater.class, new ParameterUpdater<Integer, Integer, Integer>() {
      @Override
      public Integer process(final Integer key, final Integer preValue) {
        return preValue;
      }

      @Override
      public Integer update(final Integer oldValue, final Integer deltaValue) {
        // simply add the processed value to the original value 
        return oldValue + deltaValue;
      }

      @Override
      public Integer initValue(final Integer key) {
        return 0;
      }
    });
    mockSender = injector.getInstance(MockPartitionedServerSideReplySender.class);
    server = injector.getInstance(PartitionedParameterServer.class);
  }

  /**
   * Test the performance of {@link PartitionedParameterServer} by
   * running threads that push values to and pull values from the server, concurrently.
   */
  @Test
  public void testMultiThreadPushPull() throws InterruptedException {
    final int numPushThreads = 8;
    final int numPushes = 1000000;
    final int numPullThreads = 8;
    final int numPulls = 1000000;
    final CountDownLatch countDownLatch = new CountDownLatch(numPushThreads + numPullThreads);
    final Runnable[] threads = new Runnable[numPushThreads + numPullThreads];

    for (int threadIndex = 0; threadIndex < numPushThreads; threadIndex++) {
      final int threadId = threadIndex;
      threads[threadIndex] = new Runnable() {
        @Override
        public void run() {
          for (int index = 0; index < numPushes; index++) {
            // each thread increments the server's value by 1 per push
            final int key = KEY + threadId;
            server.push(key, 1, key); // Just use key as hash for this test.
          }
          countDownLatch.countDown();
        }
      };
    }

    for (int threadIndex = 0; threadIndex < numPullThreads; threadIndex++) {
      final int threadId = threadIndex;
      threads[threadIndex + numPushThreads] = new Runnable() {
        @Override
        public void run() {
          for (int index = 0; index < numPulls; index++) {
            final int key = KEY + threadId;
            server.pull(key, "", key); // Just use key as hash for this test.
          }
          countDownLatch.countDown();
        }
      };
    }

    final long startTime = System.currentTimeMillis();
    TestUtils.runConcurrently(threads);
    final boolean allThreadsFinished = countDownLatch.await(100, TimeUnit.SECONDS);
    waitForOps();
    final long endTime = System.currentTimeMillis();
    System.out.println("Ops completed in " + (endTime - startTime) + " milliseconds");

    assertTrue(MSG_THREADS_NOT_FINISHED, allThreadsFinished);
    for (int threadIndex = 0; threadIndex < numPushThreads; threadIndex++) {
      final int key = KEY + threadIndex;
      server.pull(key, "", key); // Just use key as hash for this test.
      waitForOps();
      assertEquals(MSG_RESULT_ASSERTION, numPushes, mockSender.getLatest());
    }
  }

  private void waitForOps() throws InterruptedException {
    int opsPending = server.opsPending();
    while (opsPending > 0) {
      System.out.println("Ops Pending: " + opsPending);
      Thread.sleep(5);
      opsPending = server.opsPending();
    }
  }

  private static class MockPartitionedServerSideReplySender
      implements PartitionedServerSideReplySender<Integer, Integer> {
    private volatile int latest = -1;

    @Inject
    MockPartitionedServerSideReplySender() {
    }

    @Override
    public void sendReplyMsg(final String destId, final Integer key, final Integer value) {
      latest = value;
    }

    public int getLatest() {
      return latest;
    }
  }
}
