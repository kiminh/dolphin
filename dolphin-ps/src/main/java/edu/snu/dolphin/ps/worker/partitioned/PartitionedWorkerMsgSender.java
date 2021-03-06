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
package edu.snu.dolphin.ps.worker.partitioned;

import edu.snu.dolphin.ps.ParameterServerParameters.PreValueCodecName;
import edu.snu.dolphin.ps.avro.AvroParameterServerMsg;
import edu.snu.dolphin.ps.avro.PullMsg;
import edu.snu.dolphin.ps.avro.PushMsg;
import edu.snu.dolphin.ps.avro.Type;
import edu.snu.dolphin.ps.ns.PSNetworkSetup;
import org.apache.reef.annotations.audience.EvaluatorSide;
import org.apache.reef.exception.evaluator.NetworkException;
import org.apache.reef.io.network.Connection;
import org.apache.reef.io.serialization.Codec;
import org.apache.reef.tang.annotations.Parameter;
import org.apache.reef.wake.IdentifierFactory;

import javax.inject.Inject;
import java.nio.ByteBuffer;

/**
 * A Msg Sender for PartitionedWorker.
 */
@EvaluatorSide
public final class PartitionedWorkerMsgSender<K, P> {

  /**
   * Network Connection Service related setup required for a Parameter Server application.
   */
  private final PSNetworkSetup psNetworkSetup;

  /**
   * Required for using Network Connection Service API.
   */
  private final IdentifierFactory identifierFactory;

  /**
   * Codec for encoding PS preValues.
   */
  private final Codec<P> preValueCodec;

  @Inject
  private PartitionedWorkerMsgSender(final PSNetworkSetup psNetworkSetup,
                                     final IdentifierFactory identifierFactory,
                                     @Parameter(PreValueCodecName.class) final Codec<P> preValueCodec) {
    this.psNetworkSetup = psNetworkSetup;
    this.identifierFactory = identifierFactory;
    this.preValueCodec = preValueCodec;
  }

  private void send(final String destId, final AvroParameterServerMsg msg) {
    final Connection<AvroParameterServerMsg> conn = psNetworkSetup.getConnectionFactory()
        .newConnection(identifierFactory.getNewInstance(destId));
    try {
      conn.open();
      conn.write(msg);
    } catch (final NetworkException ex) {
      throw new RuntimeException("NetworkException during connection open/write", ex);
    }
  }

  public void sendPushMsg(final String destId, final EncodedKey<K> key, final P preValue) {
    final PushMsg pushMsg = PushMsg.newBuilder()
        .setKey(ByteBuffer.wrap(key.getEncoded()))
        .setPreValue(ByteBuffer.wrap(preValueCodec.encode(preValue)))
        .build();

    send(destId,
        AvroParameterServerMsg.newBuilder()
            .setType(Type.PushMsg)
            .setPushMsg(pushMsg)
            .build());
  }

  public void sendPullMsg(final String destId, final EncodedKey<K> key) {
    final PullMsg pullMsg = PullMsg.newBuilder()
        .setKey(ByteBuffer.wrap(key.getEncoded()))
        .setSrcId(psNetworkSetup.getMyId().toString())
        .build();

    send(destId,
        AvroParameterServerMsg.newBuilder()
            .setType(Type.PullMsg)
            .setPullMsg(pullMsg)
            .build());
  }
}
