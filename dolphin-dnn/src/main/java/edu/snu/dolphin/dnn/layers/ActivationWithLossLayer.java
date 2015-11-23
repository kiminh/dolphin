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
package edu.snu.dolphin.dnn.layers;

import edu.snu.dolphin.dnn.conf.LayerConfigurationParameters.LayerIndex;
import edu.snu.dolphin.dnn.conf.LayerConfigurationParameters.NumberOfOutput;
import edu.snu.dolphin.dnn.conf.LayerConfigurationParameters.LossFunction;
import edu.snu.dolphin.dnn.conf.NeuralNetworkConfigurationParameters.SerializedLayerConfiguartion;
import org.apache.reef.tang.Configuration;
import org.apache.reef.tang.Tang;
import org.apache.reef.tang.annotations.Parameter;
import org.apache.reef.tang.exceptions.InjectionException;
import org.apache.reef.tang.formats.ConfigurationSerializer;
import org.nd4j.linalg.api.ndarray.INDArray;

import javax.inject.Inject;
import java.io.IOException;

/**
 * Loss Layer with activation function.
 */
public final class ActivationWithLossLayer extends LayerBase {

  private final LayerBase activationLayer;
  private final String lossFunction;

  @Inject
  private ActivationWithLossLayer(@Parameter(LayerIndex.class) final int index,
                                  @Parameter(NumberOfOutput.class) final int numOutput,
                                  @Parameter(SerializedLayerConfiguartion.class) final String serializedLayerConf,
                                  final ConfigurationSerializer configurationSerializer,
                                  @Parameter(LossFunction.class) final String lossFunction) {
    super(index, numOutput);
    this.lossFunction = lossFunction;

    try {
      final Configuration layerConf = Tang.Factory.getTang().newConfigurationBuilder(
          configurationSerializer.fromString(serializedLayerConf))
          .bindNamedParameter(LayerIndex.class, String.valueOf(index)) // bind a layer index for injecting inner layer.
          .build();
      this.activationLayer = Tang.Factory.getTang().newInjector(layerConf).getInstance(LayerBase.class);
    } catch (final IOException ioException) {
      throw new RuntimeException("IOException while de-serializing a layer configuration: " + ioException);
    } catch (final InjectionException injectException) {
      throw new RuntimeException("InjectionException while injecting activation layer: " + injectException);
    }
  }

  @Override
  public boolean isLearnable() {
    return false;
  }

  @Override
  public INDArray feedForward(final INDArray input) {
    return activationLayer.feedForward(input);
  }

  /**
   * Compute the error for the specified loss function.
   * @param label the label value.
   * @param activation the activation value.
   * @param nextError an error of the next layer - this argument is ignored.
   * @return the error with respect to the activation and label values.
   */
  @Override
  public INDArray backPropagate(final INDArray label, final INDArray activation, final INDArray nextError) {
    switch (lossFunction.toLowerCase()) {
    case "cross-entropy":
      return activation.sub(label);
    default:
      throw new IllegalArgumentException("Unsupported loss function");
    }

  }

  @Override
  public LayerParameter generateParameterGradient(final INDArray input, final INDArray error) {
    throw new RuntimeException("This layer is not learnable");
  }
}
