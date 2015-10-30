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
package edu.snu.dolphin.dnn.data;

import edu.snu.dolphin.dnn.layers.LayerParameter;
import edu.snu.dolphin.dnn.util.ValidationStats;
import org.apache.reef.io.network.util.Pair;
import org.apache.reef.util.Optional;

import java.util.List;

/**
 * This class represents the data transmitted between the parameter server and worker.
 * This can contain either a pair of {@link ValidationStats}, or a list of {@link LayerParameter} arrays.
 */
public final class NeuralNetParamServerData {
  private final Optional<Pair<ValidationStats, ValidationStats>> validationStatsPair;
  private final Optional<List<LayerParameter[]>> layerParametersList;

  public NeuralNetParamServerData(final Pair<ValidationStats, ValidationStats> validationStatsPair) {
    this.validationStatsPair = Optional.of(validationStatsPair);
    this.layerParametersList = Optional.empty();
  }

  public NeuralNetParamServerData(final List<LayerParameter[]> layerParametersList) {
    this.validationStatsPair = Optional.empty();
    this.layerParametersList = Optional.of(layerParametersList);
  }

  public boolean isValidationStatsPair() {
    return this.validationStatsPair.isPresent();
  }

  public Pair<ValidationStats, ValidationStats> getValidationStatsPair() {
    return this.validationStatsPair.get();
  }

  public List<LayerParameter[]> getLayerParametersList() {
    return this.layerParametersList.get();
  }
}