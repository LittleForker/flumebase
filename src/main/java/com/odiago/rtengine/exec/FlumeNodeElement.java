// (c) Copyright 2010 Odiago, Inc.

package com.odiago.rtengine.exec;

import java.io.IOException;

import java.util.List;

import org.apache.avro.Schema;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.odiago.rtengine.exec.EventWrapper;
import com.odiago.rtengine.exec.FlowElementContext;
import com.odiago.rtengine.exec.FlowElementImpl;

import com.odiago.rtengine.flume.EmbeddedFlumeConfig;
import com.odiago.rtengine.flume.EmbeddedNode;
import com.odiago.rtengine.flume.SinkContext;
import com.odiago.rtengine.flume.SinkContextBindings;

import com.odiago.rtengine.parser.TypedField;

/**
 * FlowElement providing source data from a (possibly remote) Flume node.
 * This creates a local Flume node that acts as a receiver for the true origin node.
 * The origin Flume node has its output reconfigured to fan out to its original
 * destination as well as this local node.
 */
public class FlumeNodeElement extends FlowElementImpl {
  private static final Logger LOG = LoggerFactory.getLogger(
      FlumeNodeElement.class.getName());

  /** Flume's name for this logical node. */
  private String mFlowSourceId;

  /** The manager of the embedded Flume node instances. */
  private EmbeddedFlumeConfig mFlumeConfig;

  /**
   * The Avro record schema for event bodies emitted by this node into our
   * internal pipeline.
   */
  private Schema mOutputSchema;

  /** The fields of each record emitted by this node, and their types. */ 
  private List<TypedField> mFieldTypes;

  /** Name of the stream we are reading from. */
  private String mStreamName;

  /**
   * The name of the upstream node we are reading from.
   */
  private String mUpstreamNode;

  public FlumeNodeElement(FlowElementContext context, String flowSourceId,
      EmbeddedFlumeConfig flumeConfig, String upstreamSource, Schema outputSchema,
      List<TypedField> fieldTypes, String streamName) {
    super(context);

    mFlowSourceId = flowSourceId;
    mFlumeConfig = flumeConfig;
    mUpstreamNode = upstreamSource;
    mOutputSchema = outputSchema;
    mFieldTypes = fieldTypes;
    mStreamName = streamName;
  }

  @Override
  public void open() throws IOException {
    SinkContextBindings.get().bindContext(mFlowSourceId,
        new SinkContext(getContext(), mOutputSchema, mFieldTypes, mStreamName));
    mFlumeConfig.addFlowToForeignNode(mUpstreamNode, mFlowSourceId);
  }

  @Override
  public void close() throws IOException, InterruptedException {
    mFlumeConfig.cancelForeignConn(mUpstreamNode, mFlowSourceId);
    super.close();
  }

  @Override
  public void takeEvent(EventWrapper e) {
    // We generate our own events; nothing should be upstream from us.
    throw new RuntimeException("LocalFlumeSinkElement does not support incoming events");
  }

  @Override
  public String toString() {
    return "FlumeNode[mFlowSourceId=\"" + mFlowSourceId + "\", "
        + "mUpstreamNode=\"" + mUpstreamNode + "\"]";
  }
}