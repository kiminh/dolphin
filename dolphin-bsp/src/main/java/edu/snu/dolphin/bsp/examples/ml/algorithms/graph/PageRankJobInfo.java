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
import edu.snu.dolphin.bsp.core.StageInfo;
import edu.snu.dolphin.bsp.core.UserJobInfo;
import edu.snu.dolphin.bsp.examples.ml.data.AdjacencyListParser;
import edu.snu.dolphin.bsp.examples.ml.parameters.CommunicationGroup;
import edu.snu.dolphin.bsp.examples.ml.sub.PageRankCodec;
import edu.snu.dolphin.bsp.examples.ml.sub.PageRankReduceFunction;
import edu.snu.dolphin.bsp.examples.ml.sub.PageRankSummaryCodec;

import javax.inject.Inject;
import java.util.LinkedList;
import java.util.List;

public final class PageRankJobInfo implements UserJobInfo {

  @Inject
  public PageRankJobInfo() {
  }

  @Override
  public List<StageInfo> getStageInfoList() {
    final List<StageInfo> stageInfoList = new LinkedList<>();

    stageInfoList.add(
        StageInfo.newBuilder(PageRankCmpTask.class, PageRankCtrlTask.class, CommunicationGroup.class)
            .setBroadcast(PageRankCodec.class)
            .setReduce(PageRankSummaryCodec.class, PageRankReduceFunction.class)
            .build());

    return stageInfoList;
  }

  @Override
  public Class<? extends DataParser> getDataParser() {
    return AdjacencyListParser.class;
  }
}
