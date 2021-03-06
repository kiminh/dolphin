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
package edu.snu.dolphin.dnn.layers;

import edu.snu.dolphin.dnn.blas.Matrix;
import edu.snu.dolphin.dnn.blas.MatrixFactory;
import edu.snu.dolphin.dnn.conf.LayerConfigurationParameters.*;
import edu.snu.dolphin.dnn.layerparam.initializer.LayerParameterInitializer;
import edu.snu.dolphin.dnn.util.NeuralNetworkUtils;
import org.apache.reef.tang.annotations.Parameter;

import javax.inject.Inject;

/**
 * Pooling layer.
 *
 * This layer is not learnable.
 * This layer resizes input matrix spatially, using max pooling or average pooling.
 * This layer works for 2D and 3D inputs.
 * In a forward pass,
 * max pooling picks the maximum value in certain range (kernelHeight * kernelWidth) and these values make up output.
 * Average pooling gets the average of values in certain range (kernelHeight * kernelWidth)
 * and these values make up output.
 * In a backward pass,
 * error of each input pixel comes from errors of output pixels affected by the input pixel in feedforward step.
 */
public final class PoolingLayer extends LayerBase {

  private enum PoolType {
    AVERAGE, MAX
  }
  private final int[] outputShape;
  private final PoolType poolingType;
  private final int paddingHeight;
  private final int paddingWidth;
  private final int strideHeight;
  private final int strideWidth;
  private final int kernelHeight;
  private final int kernelWidth;
  private Matrix indexMatrix;
  private final int inputHeight;
  private final int inputWidth;
  private final int inputChannel;
  private final int outputHeight;
  private final int outputWidth;
  private final MatrixFactory matrixFactory;

  @Inject
  private PoolingLayer(@Parameter(LayerIndex.class) final int index,
                       @Parameter(LayerInputShape.class) final String inputShape,
                       @Parameter(PoolingType.class) final String poolingType,
                       @Parameter(PaddingHeight.class) final int paddingHeight,
                       @Parameter(PaddingWidth.class) final int paddingWidth,
                       @Parameter(StrideHeight.class) final int strideHeight,
                       @Parameter(StrideWidth.class) final int strideWidth,
                       @Parameter(KernelHeight.class) final int kernelHeight,
                       @Parameter(KernelWidth.class) final int kernelWidth,
                       final LayerParameterInitializer layerParameterInitializer,
                       final MatrixFactory matrixFactory) {
    super(index, inputShape);
    this.paddingHeight = paddingHeight;
    this.paddingWidth = paddingWidth;
    this.strideHeight = strideHeight;
    this.strideWidth = strideWidth;
    this.kernelHeight = kernelHeight;
    this.kernelWidth = kernelWidth;
    this.outputShape = layerParameterInitializer.getOutputShape();
    this.poolingType = PoolType.valueOf(poolingType.toUpperCase());
    this.matrixFactory = matrixFactory;
  
    if (getInputShape().length == 2) {
      this.inputChannel = 1;
      this.inputHeight = getInputShape()[0];
      this.inputWidth = getInputShape()[1];
      this.outputHeight = outputShape[0];
      this.outputWidth = outputHeight;
    } else {
      this.inputChannel = getInputShape()[0];
      this.inputHeight = getInputShape()[1];
      this.inputWidth = getInputShape()[2];
      this.outputHeight = outputShape[1];
      this.outputWidth = outputShape[2];
    }
  }

  @Override
  public int[] getOutputShape() {
    return outputShape;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isLearnable() {
    return false;
  }

  /**
   * Feedforward function for max pooling.
   * @param input the input values for this layer.
   * @return the output values for this layer.
   */
  private Matrix feedForwardMaxPooling(final Matrix input) {
    final int inputSize = inputHeight * inputWidth;
    final int outputSize = outputHeight * outputWidth;
    final int outputLength = NeuralNetworkUtils.getShapeLength(outputShape);
    final Matrix output = matrixFactory.create(outputLength, input.getColumns());
    indexMatrix = matrixFactory.create(outputLength, input.getColumns());
    for (int n = 0; n < input.getColumns(); ++n) {
      for (int c = 0; c < inputChannel; ++c) {
        for (int oh = 0; oh < outputHeight; ++oh) {
          for (int ow = 0; ow < outputWidth; ++ow) {
            //Find the maximum value within kernel range and put it in the output matrix.
            int hstart = strideHeight * oh - paddingHeight;
            int wstart = strideWidth * ow - paddingWidth;
            final int hend = Math.min(kernelHeight + hstart, inputHeight);
            final int wend = Math.min(kernelWidth + wstart, inputWidth);
            hstart = Math.max(hstart, 0);
            wstart = Math.max(wstart, 0);
            int maxIndex = c * inputSize + hstart * inputWidth + wstart;
            float max = input.get(maxIndex, n);
            for (int kh = hstart; kh < hend; ++kh) {
              for (int kw = wstart; kw < wend; ++kw) {
                final int newIndex = c * inputSize + kh * inputWidth + kw;
                final float newValue = input.get(newIndex, n);
                if (newValue > max) {
                  max = newValue;
                  maxIndex = newIndex;
                }
              }
            }
            final int outputIndex = c * outputSize + oh * outputWidth + ow;
            output.put(outputIndex, n, max);
            //Save the index of max value.
            indexMatrix.put(outputIndex, n, maxIndex);
          }
        }
      }
    }
    return output;
  }

  /**
   * Feedforward function for average pooling.
   * @param input the input values for this layer.
   * @return the output values for this layer.
   */
  private Matrix feedForwardAveragePooling(final Matrix input) {
    final int inputSize = inputHeight * inputWidth;
    final int outputSize = outputHeight * outputWidth;
    final Matrix output = matrixFactory.create(NeuralNetworkUtils.getShapeLength(outputShape), input.getColumns());
    for (int n = 0; n < input.getColumns(); ++n) {
      for (int c = 0; c < inputChannel; ++c) {
        for (int oh = 0; oh < outputHeight; ++oh) {
          for (int ow = 0; ow < outputWidth; ++ow) {
            //Compute sum of values within kernel range and put the average value in the output matrix.
            int hstart = strideHeight * oh - paddingHeight;
            int wstart = strideWidth * ow - paddingWidth;
            int hend = Math.min(kernelHeight + hstart, inputHeight + paddingHeight);
            int wend = Math.min(kernelWidth + wstart, inputWidth + paddingWidth);
            final int kernelSize = (hend - hstart) * (wend - wstart);
            hstart = Math.max(hstart, 0);
            wstart = Math.max(wstart, 0);
            hend = Math.min(hend, inputHeight);
            wend = Math.min(wend, inputWidth);
            float sum = 0;
            for (int kh = hstart; kh < hend; ++kh) {
              for (int kw = wstart; kw < wend; ++kw) {
                sum += input.get(c * inputSize + kh * inputWidth + kw, n);
              }
            }
            output.put(c * outputSize + oh * outputWidth + ow, n, sum / kernelSize);
          }
        }
      }
    }
    return output;
  }

  /**
   * Computes output values for this pooling layer.
   * available pooling type: max, average
   * @param input the input values for this layer.
   * @return the output values for this layer.
   */
  @Override
  public Matrix feedForward(final Matrix input) {
    switch (poolingType) {
    case MAX:
      return feedForwardMaxPooling(input);
    case AVERAGE:
      return feedForwardAveragePooling(input);
    default:
      throw new IllegalArgumentException("Illegal pooling type: " + poolingType);
    }
  }

  /**
   * Backpropagating function for max pooling.
   * @param input the input values for this layer.
   * @param nextError the errors of the next layer - the one closer to the output layer.
   * @return errors for this layer with the specified input value.
   */
  private Matrix backPropagateMaxPooling(final Matrix input, final Matrix nextError) {
    final Matrix error = matrixFactory.zeros(input.getRows(), input.getColumns());
    final int outputSize = outputHeight * outputWidth;
    for (int n = 0; n < input.getColumns(); ++n) {
      for (int c = 0; c < inputChannel; ++c) {
        for (int oh = 0; oh < outputHeight; ++oh) {
          for (int ow = 0; ow < outputWidth; ++ow) {
            //Add error to saved index.
            final int outputIndex = c * outputSize + oh * outputWidth + ow;
            final int maxIndex = (int) indexMatrix.get(outputIndex, n);
            final float newError = nextError.get(outputIndex, n) + error.get(maxIndex, n);
            error.put(maxIndex, n, newError);
          }
        }
      }
    }
    return error;
  }

  /**
   * Backpropagating function for average pooling.
   * @param input the input values for this layer.
   * @param nextError the errors of the next layer - the one closer to the output layer.
   * @return errors for this layer with the specified input value.
   */
  private Matrix backPropagateAveragePooling(final Matrix input, final Matrix nextError) {
    final Matrix error = matrixFactory.zeros(input.getRows(), input.getColumns());
    final int inputSize = inputHeight * inputWidth;
    final int outputSize = outputHeight * outputWidth;
    for (int n = 0; n < input.getColumns(); ++n) {
      for (int c = 0; c < inputChannel; ++c) {
        for (int oh = 0; oh < outputHeight; ++oh) {
          for (int ow = 0; ow < outputWidth; ++ow) {
            int hstart = strideHeight * oh - paddingHeight;
            int wstart = strideWidth * ow - paddingWidth;
            int hend = Math.min(kernelHeight + hstart, inputHeight + paddingHeight);
            int wend = Math.min(kernelWidth + wstart, inputWidth + paddingWidth);
            final int kernelSize = (hend - hstart) * (wend - wstart);
            hstart = Math.max(hstart, 0);
            wstart = Math.max(wstart, 0);
            hend = Math.min(hend, inputHeight);
            wend = Math.min(wend, inputWidth);
            final int outputIndex = c * outputSize + oh * outputWidth + ow;

            for (int kh = hstart; kh < hend; ++kh) {
              for (int kw = wstart; kw < wend; ++kw) {
                //Add error divided by kernel size for all pixels within the range.
                final int inputIndex = c * inputSize + kh * inputWidth + kw;
                final float newError = nextError.get(outputIndex, n) / kernelSize + error.get(inputIndex, n);
                error.put(inputIndex, n, newError);
              }
            }
          }
        }
      }
    }
    return error;
  }

  /**
   * Computes errors for this pooling layer.
   * available pooling type: max, average
   * @param input the input values for this layer.
   * @param activation the output values.
   * @param nextError the errors of the next layer - the one closer to the output layer.
   * @return errors for this layer with the specified input value.
   */
  @Override
  public Matrix backPropagate(final Matrix input, final Matrix activation, final Matrix nextError) {
    switch (poolingType) {
    case MAX:
      return backPropagateMaxPooling(input, nextError);
    case AVERAGE:
      return backPropagateAveragePooling(input, nextError);
    default:
      throw new IllegalArgumentException("Illegal pooling type: " + poolingType);
    }
  }

  /** {@inheritDoc} */
  @Override
  public LayerParameter generateParameterGradient(final Matrix input, final Matrix error) {
    throw new RuntimeException("This layer is not learnable");
  }
}
