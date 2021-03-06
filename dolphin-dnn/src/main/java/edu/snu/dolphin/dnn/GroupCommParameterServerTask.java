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
package edu.snu.dolphin.dnn;

import edu.snu.dolphin.bsp.examples.ml.parameters.MaxIterations;
import edu.snu.dolphin.dnn.conf.NeuralNetworkConfigurationParameters.*;
import edu.snu.dolphin.dnn.layers.LayerParameter;
import edu.snu.dolphin.dnn.util.ValidationStats;
import org.apache.reef.io.network.group.api.operators.Broadcast;
import org.apache.reef.io.network.group.api.operators.Reduce;
import org.apache.reef.io.network.group.api.task.CommunicationGroupClient;
import org.apache.reef.io.network.group.api.task.GroupCommClient;
import org.apache.reef.io.network.util.Pair;
import org.apache.reef.tang.Configuration;
import org.apache.reef.tang.Injector;
import org.apache.reef.tang.annotations.Parameter;
import org.apache.reef.tang.formats.ConfigurationSerializer;
import org.apache.reef.task.Task;

import javax.inject.Inject;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import static edu.snu.dolphin.dnn.NeuralNetworkTask.*;
import static edu.snu.dolphin.dnn.util.NeuralNetworkUtils.*;

/**
 * Task that acts as a parameter server for {@link GroupCommNeuralNetworkTask}s using REEF Group Communication.
 * <p/>
 * Receives parameter gradients from Tasks, computes updated parameters using those values,
 * and finally sends the updates back to the Tasks.
 */
public final class GroupCommParameterServerTask implements Task {
  private static final Logger LOG = Logger.getLogger(GroupCommParameterServerTask.class.getName());
  public static final String TASK_ID = GroupCommParameterServerTask.class.getSimpleName();

  private final LayerParameter[] layerParameters;
  private final float stepsize;
  private final int maxIterations;
  private final Broadcast.Sender<LayerParameter[]> layerParamBroadcastSender;
  private final Reduce.Receiver<Pair<Integer, LayerParameter[]>> parameterGradientReduceReceiver;
  private final Reduce.Receiver<Pair<ValidationStats, ValidationStats>> validationStatsPairReduceReceiver;

  @Inject
  private GroupCommParameterServerTask(
      @Parameter(SerializedLayerConfigurationSet.class) final Set<String> serializedLayerConfigurationSet,
      @Parameter(Stepsize.class) final float stepsize,
      @Parameter(MaxIterations.class) final int maxIterations,
      @Parameter(InputShape.class) final String inputShape,
      final ConfigurationSerializer configurationSerializer,
      final GroupCommClient groupCommClient,
      final Injector injector) {

    final CommunicationGroupClient commGroup =
        groupCommClient.getCommunicationGroup(NeuralNetworkGroupCommDriver.NeuralNetworkCommGroup.class);
    this.layerParamBroadcastSender =
        commGroup.getBroadcastSender(NeuralNetworkGroupCommDriver.LayerParamBroadcast.class);
    this.parameterGradientReduceReceiver =
        commGroup.getReduceReceiver(NeuralNetworkGroupCommDriver.ParameterGradientReduce.class);
    this.validationStatsPairReduceReceiver =
        commGroup.getReduceReceiver(NeuralNetworkGroupCommDriver.ValidationStatsPairReduce.class);

    final Configuration[] layerParamInitializerConfs =
        deserializeLayerConfSetToArray(configurationSerializer, serializedLayerConfigurationSet);
    this.layerParameters = getInitialLayerParameters(injector, layerParamInitializerConfs, inputShape);
    this.stepsize = stepsize;
    this.maxIterations = maxIterations;
  }

  @Override
  public byte[] call(final byte[] bytes) throws Exception {
    LOG.log(Level.INFO, "GroupCommParameterServerTask.call() commencing....");
    long loopIndex = 0;
    int iteration = 0;

    // The variable `iteration` does not indicate the number of times this while loop has ran.
    // Rather, `iteration` tracks the number of iterations `GroupCommNeuralNetworkTask`s have finished up until now.
    while (iteration < maxIterations) {
      LOG.log(Level.INFO, "GroupCommParameterServerTask.call() loop {0}....", loopIndex++);
      final Pair<Integer, LayerParameter[]> result = parameterGradientReduceReceiver.reduce();

      if (result.getFirst() == 0) {
        // All Tasks have finished this iteration. Let's end the iteration.
        layerParamBroadcastSender.send(new LayerParameter[0]);
        final Pair<ValidationStats, ValidationStats> validationStatsPair = validationStatsPairReduceReceiver.reduce();
        LOG.log(Level.INFO,
            generateIterationLog(validationStatsPair.getFirst(), validationStatsPair.getSecond(), iteration));
        iteration++;
        continue;
      }

      final int batchSizeSum = result.getFirst();
      final LayerParameter[] deltaLayerParameters = result.getSecond();

      // apply the updates, regarding the size of the batch and the step size
      for (int index = 0; index < layerParameters.length; ++index) {
        final LayerParameter layerParameter = layerParameters[index];
        final LayerParameter deltaLayerParameter = deltaLayerParameters[index];
        final float factor = stepsize / batchSizeSum;
        layerParameter.getWeightParam().subi(deltaLayerParameter.getWeightParam().muli(factor));
        layerParameter.getBiasParam().subi(deltaLayerParameter.getBiasParam().muli(factor));
      }

      layerParamBroadcastSender.send(layerParameters);
    }

    LOG.log(Level.INFO, "GroupCommParameterServerTask.call() terminating....");
    return null;
  }
}
