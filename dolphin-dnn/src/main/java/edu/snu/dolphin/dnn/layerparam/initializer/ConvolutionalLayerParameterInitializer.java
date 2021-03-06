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
package edu.snu.dolphin.dnn.layerparam.initializer;

import edu.snu.dolphin.dnn.blas.Matrix;
import edu.snu.dolphin.dnn.blas.MatrixFactory;
import edu.snu.dolphin.dnn.conf.LayerConfigurationParameters;
import edu.snu.dolphin.dnn.layers.LayerParameter;
import org.apache.reef.tang.annotations.Parameter;

import javax.inject.Inject;

import static edu.snu.dolphin.dnn.util.NeuralNetworkUtils.shapeFromString;

/**
 * Convolutional Layer parameter initializer.
 *
 * This class initializes the weight matrix
 * with pseudo random normal distributed value with mean 0 and given standard deviation.
 * This class initializes the bias vector with the given value.
 * This class includes function that computes the output shape.
 */
public final class ConvolutionalLayerParameterInitializer implements LayerParameterInitializer {

  private final MatrixFactory matrixFactory;
  private final int index;
  private final int[] inputShape;
  private final int[] outputShape;
  private final int paddingHeight;
  private final int paddingWidth;
  private final int strideHeight;
  private final int strideWidth;
  private final int kernelHeight;
  private final int kernelWidth;
  private final float initWeight;
  private final float initBias;
  private final long randomSeed;
  private final int numOutput;
  private final int inputHeight;
  private final int inputWidth;
  private final int inputChannel;

  @Inject
  private ConvolutionalLayerParameterInitializer(
      final MatrixFactory matrixFactory,
      @Parameter(LayerConfigurationParameters.LayerIndex.class) final int index,
      @Parameter(LayerConfigurationParameters.LayerInputShape.class) final String inputShape,
      @Parameter(LayerConfigurationParameters.PaddingHeight.class) final int paddingHeight,
      @Parameter(LayerConfigurationParameters.PaddingWidth.class) final int paddingWidth,
      @Parameter(LayerConfigurationParameters.StrideHeight.class) final int strideHeight,
      @Parameter(LayerConfigurationParameters.StrideWidth.class) final int strideWidth,
      @Parameter(LayerConfigurationParameters.KernelHeight.class) final int kernelHeight,
      @Parameter(LayerConfigurationParameters.KernelWidth.class) final int kernelWidth,
      @Parameter(LayerConfigurationParameters.RandomSeed.class) final long randomSeed,
      @Parameter(LayerConfigurationParameters.InitialWeight.class) final float initWeight,
      @Parameter(LayerConfigurationParameters.InitialBias.class) final float initBias,
      @Parameter(LayerConfigurationParameters.NumberOfOutput.class) final int numOutput) {
    this.matrixFactory = matrixFactory;
    this.index = index;
    this.inputShape = shapeFromString(inputShape);
    this.paddingHeight = paddingHeight;
    this.paddingWidth = paddingWidth;
    this.strideHeight = strideHeight;
    this.strideWidth = strideWidth;
    this.kernelHeight = kernelHeight;
    this.kernelWidth = kernelWidth;
    this.numOutput = numOutput;
    this.randomSeed = randomSeed;
    this.initWeight = initWeight;
    this.initBias = initBias;

    if (this.inputShape.length == 2) {
      this.inputChannel = 1;
      this.inputHeight = this.inputShape[0];
      this.inputWidth = this.inputShape[1];
    } else {
      this.inputChannel = this.inputShape[0];
      this.inputHeight = this.inputShape[1];
      this.inputWidth = this.inputShape[2];
    }
    this.outputShape = computeOutputShape();
  }

  /**
   * @return the initial parameter of the layer.
   */
  public LayerParameter generateInitialParameter() {
    final Matrix weight = matrixFactory.randn(kernelHeight * kernelWidth * inputChannel, numOutput, randomSeed);
    final Matrix bias = matrixFactory.create(outputShape[1] * outputShape[2] * numOutput, 1).fill(initBias);

    weight.muli(initWeight); // multiply by standard deviation.

    return LayerParameter.newBuilder()
        .setWeightParam(weight)
        .setBiasParam(bias)
        .build();
  }

  /**
   * @return the index of the layer.
   */
  public int getIndex() {
    return index;
  }

  /**
   * This function computes output shape.
   * input shape: row * col
   * output shape: row' * col'
   * row' = ceil((row − kernelHeight + 2 * paddingHeight) / stride) + 1
   * col' = ceil((col − kernelWidth + 2 * paddingWidth) / stride) + 1
   * @return shape of output
   */
  private int[] computeOutputShape() {
    final int[] computedShape = new int[3];
    if (inputShape.length != 2 && inputShape.length != 3) {
      throw new IllegalArgumentException("Unsupported input dimensions: " + inputShape.length);
    }
    computedShape[0] = numOutput;
    computedShape[1] = (int) Math.ceil((float) (inputHeight - kernelHeight + 2 * paddingHeight) / strideHeight) + 1;
    computedShape[2] = (int) Math.ceil((float) (inputWidth - kernelWidth + 2 * paddingWidth) / strideWidth) + 1;
    if (computedShape[0] < 0 || computedShape[1] < 0 || computedShape[2] < 0) {
      throw new IllegalArgumentException("Negative output size");
    }
    return computedShape;
  }

  /**
   * @return shape of output
   */
  @Override
  public int[] getOutputShape() {
    return outputShape;
  }
}
