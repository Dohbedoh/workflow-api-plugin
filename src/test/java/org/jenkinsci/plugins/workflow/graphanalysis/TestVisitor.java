package org.jenkinsci.plugins.workflow.graphanalysis;

import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.junit.Assert;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Test visitor class, tracks invocations of methods
 */
public class TestVisitor implements SimpleChunkVisitor {
    public enum CallType {
        ATOM_NODE,
        CHUNK_START,
        CHUNK_END,
        PARALLEL_START,
        PARALLEL_END,
        PARALLEL_BRANCH_START,
        PARALLEL_BRANCH_END
    }

    public static class CallEntry {
        CallType type;
        int[] ids = {-1, -1, -1, -1};

        public void setIds(FlowNode... nodes) {
            for (int i=0; i<nodes.length; i++) {
                if (nodes[i] == null) {
                    ids[i] = -1;
                } else {
                    ids[i] = Integer.parseInt(nodes[i].getId());
                }
            }
        }

        public CallEntry(CallType type, FlowNode... nodes) {
            this.type = type;
            this.setIds(nodes);
        }

        public CallEntry(CallType type, int... vals) {
            this.type = type;
            for (int i=0; i<vals.length; i++){
                ids[i]=vals[i];
            }
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || !(o instanceof CallEntry)) {
                return false;
            }
            CallEntry entry = (CallEntry)o;
            return this.type == entry.type && Arrays.equals(this.ids, entry.ids);
        }

        public void assertEquals(CallEntry test) {
            Assert.assertNotNull(test);
            Assert.assertNotNull(test.type);
            Assert.assertArrayEquals(this.ids, test.ids);
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder("CallEntry: ")
                    .append(type).append('-');
            switch (type) {
                case ATOM_NODE:
                    builder.append("Before/Current/After:")
                            .append(ids[0]).append('/')
                            .append(ids[1]).append('/')
                            .append(ids[2]);
                    break;
                case CHUNK_START:
                    builder.append("StartNode/BeforeNode:")
                            .append(ids[0]).append('/')
                            .append(ids[1]);
                    break;
                case CHUNK_END:
                    builder.append("EndNode/AfterNode:")
                            .append(ids[0]).append('/')
                            .append(ids[1]);
                    break;
                case PARALLEL_START:
                    builder.append("ParallelStartNode/OneBranchStartNode:")
                            .append(ids[0]).append('/')
                            .append(ids[1]);
                    break;
                case PARALLEL_END:
                    builder.append("ParallelStartNode/ParallelEndNode:")
                            .append(ids[0]).append('/')
                            .append(ids[1]);
                    break;
                case PARALLEL_BRANCH_START:
                    builder.append("ParallelStart/BranchStart:")
                            .append(ids[0]).append('/')
                            .append(ids[1]);
                    break;
                case PARALLEL_BRANCH_END:
                    builder.append("ParallelStart/BranchEnd:")
                            .append(ids[0]).append('/')
                            .append(ids[1]);
                    break;
            }
            return builder.toString();
        }

    }

    public ArrayList<CallEntry> calls = new ArrayList<CallEntry>();

    @Override
    public void chunkStart(@Nonnull FlowNode startNode, @CheckForNull FlowNode beforeBlock, @Nonnull ForkScanner scanner) {
        calls.add(new CallEntry(CallType.CHUNK_START, startNode, beforeBlock));
    }

    @Override
    public void chunkEnd(@Nonnull FlowNode endNode, @CheckForNull FlowNode afterChunk, @Nonnull ForkScanner scanner) {
        calls.add(new CallEntry(CallType.CHUNK_END, endNode, afterChunk));
    }

    @Override
    public void parallelStart(@Nonnull FlowNode parallelStartNode, @Nonnull FlowNode branchNode, @Nonnull ForkScanner scanner) {
        calls.add(new CallEntry(CallType.PARALLEL_START, parallelStartNode, branchNode));
    }

    @Override
    public void parallelEnd(@Nonnull FlowNode parallelStartNode, @Nonnull FlowNode parallelEndNode, @Nonnull ForkScanner scanner) {
        calls.add(new CallEntry(CallType.PARALLEL_END, parallelStartNode, parallelEndNode));
    }

    @Override
    public void parallelBranchStart(@Nonnull FlowNode parallelStartNode, @Nonnull FlowNode branchStartNode, @Nonnull ForkScanner scanner) {
        calls.add(new CallEntry(CallType.PARALLEL_BRANCH_START, parallelStartNode, branchStartNode));
    }

    @Override
    public void parallelBranchEnd(@Nonnull FlowNode parallelStartNode, @Nonnull FlowNode branchEndNode, @Nonnull ForkScanner scanner) {
        calls.add(new CallEntry(CallType.PARALLEL_BRANCH_END, parallelStartNode, branchEndNode));
    }

    @Override
    public void atomNode(@CheckForNull FlowNode before, @Nonnull FlowNode atomNode, @CheckForNull FlowNode after, @Nonnull ForkScanner scan) {
        calls.add(new CallEntry(CallType.ATOM_NODE, before, atomNode, after));
    }

    public void assertNoDupes() throws Exception {
        List<CallEntry> entries = new ArrayList<CallEntry>();
        for (CallEntry ce : this.calls) {
            if (entries.contains(ce)) {
                Assert.fail("Duplicate call: "+ce.toString());
            }
        }
    }

    public void assertMatchingParallelBranchStartEnd() throws Exception {
        // Map the parallel start node to the start/end nodes for all branches
        HashMap<Integer, List<Integer>> branchStartIds = new HashMap<Integer, List<Integer>>();
        HashMap<Integer, List<Integer>> branchEndIds = new HashMap<Integer, List<Integer>>();

        for (CallEntry ce : this.calls) {
            if (ce.type == CallType.PARALLEL_BRANCH_END) {
                List<Integer> ends = branchEndIds.get(ce.ids[0]);
                if (ends == null) {
                    ends = new ArrayList<Integer>();
                }
                ends.add(ce.ids[1]);
                branchEndIds.put(ce.ids[0], ends);
            } else if (ce.type == CallType.PARALLEL_BRANCH_START) {
                List<Integer> ends = branchStartIds.get(ce.ids[0]);
                if (ends == null) {
                    ends = new ArrayList<Integer>();
                }
                ends.add(ce.ids[1]);
                branchStartIds.put(ce.ids[0], ends);
            }
        }

        // First check every parallel with branch starts *also* has branch ends and the same number of them
        for (Map.Entry<Integer, List<Integer>> startEntry : branchStartIds.entrySet()) {
            List<Integer> ends = branchEndIds.get(startEntry.getKey());
            Assert.assertNotNull("Parallels with a branch start event(s) but no branch end event(s), parallel start node id: "+startEntry.getKey(), ends);
            Assert.assertEquals("Parallels must have matching numbers of start and end events, but don't -- for parallel starting with: "+
                startEntry.getKey(), startEntry.getValue().size(), ends.size());
        }

        // Verify the reverse is true: if we have a branch end, there are branch starts (count equality was checked above)
        for (Map.Entry<Integer, List<Integer>> endEntry : branchEndIds.entrySet()) {
            List<Integer> starts = branchStartIds.get(endEntry.getKey());
            Assert.assertNotNull("Parallels with a branch end event(s) but no matching branch start event(s), parallel start node id: "+endEntry.getKey(), starts);
        }
    }

    /** Verify that we have balanced start/end for parallels */
    public void assertMatchingParallelStartEnd() throws Exception {
        // It's like balancing parentheses, starts and ends must be equal
        ArrayDeque<Integer> openParallelStarts = new ArrayDeque<Integer>();

        for (CallEntry ce : this.calls) {
            if (ce.type == CallType.PARALLEL_END) {
                openParallelStarts.push(ce.ids[0]);
            } else if (ce.type == CallType.PARALLEL_START) {
                if (openParallelStarts.size() > 0) {
                    Assert.assertEquals("Parallel start and end events must point to the same parallel start node ID",
                            openParallelStarts.peekLast(), new Integer(ce.ids[0])
                    );
                    openParallelStarts.pop();
                }
                // More parallel starts than ends is *legal* because we may have an in-progress parallel without an end created.
            }
        }

        if (openParallelStarts.size() > 0) {
            StringBuilder sb = new StringBuilder();
            for (Integer parallelStartId : openParallelStarts) {
                sb.append(parallelStartId).append(',');
            }
            Assert.fail("Parallel ends with no starts, for parallel(s) with start nodes IDs: "+sb.toString());
        }
    }
}
