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
package edu.snu.dolphin.bsp.examples.ml.algorithms.graph;

import edu.snu.dolphin.bsp.core.DataParser;
import edu.snu.dolphin.bsp.core.ParseException;
import edu.snu.dolphin.bsp.core.UserComputeTask;
import edu.snu.dolphin.bsp.examples.ml.data.PageRankSummary;
import edu.snu.dolphin.bsp.groupcomm.interfaces.DataBroadcastReceiver;
import edu.snu.dolphin.bsp.groupcomm.interfaces.DataReduceSender;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PageRank algorithm compute class.
 *
 * Reference
 * - http://en.wikipedia.org/wiki/PageRank
 * - https://github.com/apache/spark/blob/master/graphx/src/main/scala/org/apache/spark/graphx/lib/PageRank.scala
 */
public class PageRankCmpTask extends UserComputeTask
    implements DataReduceSender<PageRankSummary>, DataBroadcastReceiver<Map<Integer, Double>> {

  /**
   * Adjacency list data parser.
   */
  private final DataParser<Map<Integer, List<Integer>>> dataParser;

  /**
   * Subgraph consists a node and its outgoing neighbor set.
   */
  private Map<Integer, List<Integer>> subgraphs;

  /**
   * Map of current rank.
   */
  private Map<Integer, Double> rank;

  /**
   * Map of contributed increment to outgoing neighbor nodes.
   */
  private final Map<Integer, Double> increment = new HashMap<>();

  /**
   * Constructs a single Compute Task for PageRank.
   * This class is instantiated by TANG.
   * @param dataParser
   */
  @Inject
  public PageRankCmpTask(final DataParser<Map<Integer, List<Integer>>> dataParser) {
    this.dataParser = dataParser;
  }

  /**
   * Load a split subgraph and set the initial rank for each node.
   * @throws ParseException
   */
  @Override
  public void initialize() throws ParseException {
    subgraphs = dataParser.get();
    rank = new HashMap<>();
    for (final Integer key : subgraphs.keySet()) {
      rank.put(key, 1.0d);
    }
  }

  @Override
  public final void run(final int iteration) {
    increment.clear();
    for (final Map.Entry<Integer, List<Integer>> entry : subgraphs.entrySet()) {
      final Integer nodeId = entry.getKey();
      final List<Integer> outList = entry.getValue();

      // Add itself to ensure existence in graph
      if (!increment.containsKey(nodeId)) {
        increment.put(nodeId, 0.0d);
      }
      // Skip distribution if it has no outbound neighbor
      if (outList.size() == 0) {
        continue;
      }

      // The rank of node is distributed evenly to its outbound neighbor nodes.
      // Compute a contribution per each node
      final double contribution = rank.get(nodeId) * 1.0d / outList.size();
      // Add the contribution to each node
      for (final Integer adjNode : outList) {
        if (increment.containsKey(adjNode)) {
          increment.put(adjNode, increment.get(adjNode) + contribution);
        } else {
          increment.put(adjNode, contribution);
        }
      }
    }
  }

  /**
   * Receive the updated rank of all nodes from previous run.
   * At the iteration 0, the rank is empty.
   * @param iteration
   * @param rankData
   */
  @Override
  public void receiveBroadcastData(final int iteration, final Map<Integer, Double> rankData) {
    if (iteration < 1) {
      return;
    }
    this.rank = rankData;
  }

  /**
   * Send the new increment for outgoing link nodes in this subgraph.
   * @param iteration
   * @return
   */
  @Override
  public PageRankSummary sendReduceData(final int iteration) {
    return new PageRankSummary(this.increment);
  }
}
