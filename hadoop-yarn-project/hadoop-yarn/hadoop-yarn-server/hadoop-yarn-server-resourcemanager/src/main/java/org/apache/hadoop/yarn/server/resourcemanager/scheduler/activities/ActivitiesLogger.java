/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.yarn.server.resourcemanager.scheduler.activities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.hadoop.yarn.api.records.ApplicationId;
import org.apache.hadoop.yarn.api.records.ContainerId;
import org.apache.hadoop.yarn.api.records.NodeId;
import org.apache.hadoop.yarn.api.records.Priority;
import org.apache.hadoop.yarn.server.resourcemanager.rmcontainer.RMContainer;
import org.apache.hadoop.yarn.server.resourcemanager.scheduler.SchedulerApplicationAttempt;
import org.apache.hadoop.yarn.server.resourcemanager.scheduler.SchedulerNode;
import org.apache.hadoop.yarn.server.resourcemanager.scheduler.common.fica.FiCaSchedulerNode;
import org.apache.hadoop.yarn.server.scheduler.SchedulerRequestKey;

/**
 * Utility for logging scheduler activities
 */
// FIXME: make sure CandidateNodeSet works with this class
public class ActivitiesLogger {
  private static final Logger LOG =
      LoggerFactory.getLogger(ActivitiesLogger.class);

  /**
   * Methods for recording activities from an app
   */
  public static class APP {

    /*
     * Record skipped application activity when no container allocated /
     * reserved / re-reserved. Scheduler will look at following applications
     * within the same leaf queue.
     */
    public static void recordSkippedAppActivityWithoutAllocation(
        ActivitiesManager activitiesManager, SchedulerNode node,
        SchedulerApplicationAttempt application,
        SchedulerRequestKey requestKey,
        String diagnostic) {
      recordAppActivityWithoutAllocation(activitiesManager, node, application,
          requestKey, diagnostic, ActivityState.SKIPPED);
    }

    /*
     * Record application activity when rejected because of queue maximum
     * capacity or user limit.
     */
    public static void recordRejectedAppActivityFromLeafQueue(
        ActivitiesManager activitiesManager, SchedulerNode node,
        SchedulerApplicationAttempt application, Priority priority,
        String diagnostic) {
      if (activitiesManager == null) {
        return;
      }
      NodeId nodeId = getRecordingNodeId(activitiesManager, node);
      if (activitiesManager.shouldRecordThisNode(nodeId)) {
        recordActivity(activitiesManager, nodeId, application.getQueueName(),
            application.getApplicationId().toString(), priority,
            ActivityState.REJECTED, diagnostic, "app");
      }
      finishSkippedAppAllocationRecording(activitiesManager,
          application.getApplicationId(), ActivityState.REJECTED, diagnostic);
    }

    /*
     * Record application activity when no container allocated /
     * reserved / re-reserved. Scheduler will look at following applications
     * within the same leaf queue.
     */
    public static void recordAppActivityWithoutAllocation(
        ActivitiesManager activitiesManager, SchedulerNode node,
        SchedulerApplicationAttempt application,
        SchedulerRequestKey schedulerKey,
        String diagnostic, ActivityState appState) {
      if (activitiesManager == null) {
        return;
      }
      NodeId nodeId = getRecordingNodeId(activitiesManager, node);
      if (activitiesManager.shouldRecordThisNode(nodeId)) {
        if (schedulerKey != null) {
          String allocationRequestId =
              String.valueOf(schedulerKey.getAllocationRequestId());
          String priorityStr = getPriorityStr(schedulerKey);
          String requestName = getRequestName(priorityStr, allocationRequestId);
          String type = "container";
          // Add application-container activity into specific node allocation.
          activitiesManager.addSchedulingActivityForNode(nodeId,
              requestName, null,
              priorityStr, ActivityState.SKIPPED, diagnostic, type,
              null);
          type = "request";
          // Add application-container activity into specific node allocation.
          activitiesManager.addSchedulingActivityForNode(nodeId,
              application.getApplicationId().toString(), requestName,
              priorityStr, ActivityState.SKIPPED,
              ActivityDiagnosticConstant.EMPTY, type, allocationRequestId);
        }
        // Add queue-application activity into specific node allocation.
        activitiesManager.addSchedulingActivityForNode(nodeId,
            application.getQueueName(),
            application.getApplicationId().toString(),
            application.getPriority().toString(), ActivityState.SKIPPED,
            schedulerKey != null ? ActivityDiagnosticConstant.EMPTY :
                diagnostic, "app", null);
      }
      // Add application-container activity into specific application allocation
      // Under this condition, it fails to allocate a container to this
      // application, so containerId is null.
      if (activitiesManager.shouldRecordThisApp(
          application.getApplicationId())) {
        String type = "container";
        activitiesManager.addSchedulingActivityForApp(
            application.getApplicationId(), null,
            getPriorityStr(schedulerKey), appState,
            diagnostic, type, nodeId,
            schedulerKey == null ?
                null : String.valueOf(schedulerKey.getAllocationRequestId()));
      }
    }

    /*
     * Record application activity when container allocated / reserved /
     * re-reserved
     */
    public static void recordAppActivityWithAllocation(
        ActivitiesManager activitiesManager, SchedulerNode node,
        SchedulerApplicationAttempt application, RMContainer updatedContainer,
        ActivityState activityState) {
      if (activitiesManager == null) {
        return;
      }
      NodeId nodeId = getRecordingNodeId(activitiesManager, node);
      if (nodeId == null || nodeId == ActivitiesManager.EMPTY_NODE_ID) {
        nodeId = updatedContainer.getNodeId();
      }
      if (activitiesManager.shouldRecordThisNode(nodeId)) {
        String containerPriorityStr =
            updatedContainer.getContainer().getPriority().toString();
        String allocationRequestId = String
            .valueOf(updatedContainer.getContainer().getAllocationRequestId());
        String requestName =
            getRequestName(containerPriorityStr, allocationRequestId);
        String type = "container";

        // Add application-container activity into specific node allocation.
        activitiesManager.addSchedulingActivityForNode(nodeId,
            requestName,
            updatedContainer.getContainer().toString(),
            containerPriorityStr,
            activityState, ActivityDiagnosticConstant.EMPTY, type, null);
        type = "request";
        // Add application-container activity into specific node allocation.
        activitiesManager.addSchedulingActivityForNode(nodeId,
            application.getApplicationId().toString(),
            requestName, containerPriorityStr,
            activityState, ActivityDiagnosticConstant.EMPTY, type,
            allocationRequestId);
        type = "app";
        // Add queue-application activity into specific node allocation.
        activitiesManager.addSchedulingActivityForNode(nodeId,
            application.getQueueName(),
            application.getApplicationId().toString(),
            application.getPriority().toString(), ActivityState.ACCEPTED,
            ActivityDiagnosticConstant.EMPTY, type, null);
      }
      // Add application-container activity into specific application allocation
      if (activitiesManager.shouldRecordThisApp(
          application.getApplicationId())) {
        String type = "container";
        activitiesManager.addSchedulingActivityForApp(
            application.getApplicationId(),
            updatedContainer.getContainerId(),
            updatedContainer.getContainer().getPriority().toString(),
            activityState, ActivityDiagnosticConstant.EMPTY, type, nodeId,
            String.valueOf(
                updatedContainer.getContainer().getAllocationRequestId()));
      }
    }

    /*
     * Invoked when scheduler starts to look at this application within one node
     * update.
     */
    public static void startAppAllocationRecording(
        ActivitiesManager activitiesManager, FiCaSchedulerNode node,
        long currentTime,
        SchedulerApplicationAttempt application) {
      if (activitiesManager == null) {
        return;
      }
      NodeId nodeId = getRecordingNodeId(activitiesManager, node);
      activitiesManager
          .startAppAllocationRecording(nodeId, currentTime,
              application);
    }

    /*
     * Invoked when scheduler finishes looking at this application within one
     * node update, and the app has any container allocated/reserved during
     * this allocation.
     */
    public static void finishAllocatedAppAllocationRecording(
        ActivitiesManager activitiesManager, ApplicationId applicationId,
        ContainerId containerId, ActivityState containerState,
        String diagnostic) {
      if (activitiesManager == null) {
        return;
      }

      if (activitiesManager.shouldRecordThisApp(applicationId)) {
        activitiesManager.finishAppAllocationRecording(applicationId,
            containerId, containerState, diagnostic);
      }
    }

    /*
     * Invoked when scheduler finishes looking at this application within one
     * node update, and the app DOESN'T have any container allocated/reserved
     * during this allocation.
     */
    public static void finishSkippedAppAllocationRecording(
        ActivitiesManager activitiesManager, ApplicationId applicationId,
        ActivityState containerState, String diagnostic) {
      finishAllocatedAppAllocationRecording(activitiesManager, applicationId,
          null, containerState, diagnostic);
    }
  }

  /**
   * Methods for recording activities from a queue
   */
  public static class QUEUE {
    /*
     * Record activities of a queue
     */
    public static void recordQueueActivity(ActivitiesManager activitiesManager,
        SchedulerNode node, String parentQueueName, String queueName,
        ActivityState state, String diagnostic) {
      if (activitiesManager == null) {
        return;
      }
      NodeId nodeId = getRecordingNodeId(activitiesManager, node);
      if (activitiesManager.shouldRecordThisNode(nodeId)) {
        recordActivity(activitiesManager, nodeId, parentQueueName, queueName,
            null, state, diagnostic, null);
      }
    }
  }

  /**
   * Methods for recording overall activities from one node update
   */
  public static class NODE {

    /*
     * Invoked when node allocation finishes, and there's NO container
     * allocated or reserved during the allocation
     */
    public static void finishSkippedNodeAllocation(
        ActivitiesManager activitiesManager, SchedulerNode node) {
      finishAllocatedNodeAllocation(activitiesManager, node, null,
          AllocationState.SKIPPED);
    }

    /*
     * Invoked when node allocation finishes, and there's any container
     * allocated or reserved during the allocation
     */
    public static void finishAllocatedNodeAllocation(
        ActivitiesManager activitiesManager, SchedulerNode node,
        ContainerId containerId, AllocationState containerState) {
      NodeId nodeId = getRecordingNodeId(activitiesManager, node);
      if (nodeId == null) {
        return;
      }
      if (activitiesManager.shouldRecordThisNode(nodeId)) {
        activitiesManager.updateAllocationFinalState(nodeId,
            containerId, containerState);
      }
    }

    /*
     * Invoked when node heartbeat finishes
     */
    public static void finishNodeUpdateRecording(
        ActivitiesManager activitiesManager, NodeId nodeID) {
      if (activitiesManager == null) {
        return;
      }
      activitiesManager.finishNodeUpdateRecording(nodeID);
    }

    /*
     * Invoked when node heartbeat starts
     */
    public static void startNodeUpdateRecording(
        ActivitiesManager activitiesManager, NodeId nodeID) {
      if (activitiesManager == null) {
        return;
      }
      activitiesManager.startNodeUpdateRecording(nodeID);
    }
  }

  // Add queue, application or container activity into specific node allocation.
  private static void recordActivity(ActivitiesManager activitiesManager,
      NodeId nodeId, String parentName, String childName,
      Priority priority, ActivityState state, String diagnostic, String type) {
    activitiesManager.addSchedulingActivityForNode(nodeId, parentName,
        childName, priority != null ? priority.toString() : null, state,
        diagnostic, type, null);
  }

  private static NodeId getRecordingNodeId(ActivitiesManager activitiesManager,
      SchedulerNode node) {
    return activitiesManager == null ? null :
        activitiesManager.getRecordingNodeId(node);
  }

  private static String getRequestName(String priority,
      String allocationRequestId) {
    return "request_"
        + (priority == null ? "" : priority)
        + "_" + (allocationRequestId == null ? "" : allocationRequestId);
  }

  private static String getPriorityStr(SchedulerRequestKey schedulerKey) {
    Priority priority = schedulerKey == null ?
        null : schedulerKey.getPriority();
    return priority == null ? null : priority.toString();
  }
}
