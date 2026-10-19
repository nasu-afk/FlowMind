import { z } from "zod";

export const nodeSchema = z.object({
  id: z.string().min(1).max(120),
  type: z.string().min(1).max(120),
  version: z.number().int().positive(),
  configuration: z.record(z.string(), z.unknown()).default({}),
  sideEffecting: z.boolean().default(false),
});
export const edgeSchema = z.object({
  id: z.string().min(1).max(120), source: z.string().min(1), target: z.string().min(1), branch: z.string().max(120).nullable().optional()
});
export const workflowGraphSchema = z.object({ nodes: z.array(nodeSchema).min(1).max(2000), edges: z.array(edgeSchema).max(10000).default([]) }).superRefine((graph, context) => {
  const ids = new Set<string>();
  for (const [index,node] of graph.nodes.entries()) {
    if (ids.has(node.id)) context.addIssue({ code: "custom", message: `Duplicate node id: ${node.id}`, path: ["nodes",index,"id"] });
    ids.add(node.id);
    if (node.type === "loop") {
      const bound=node.configuration.maxIterations;
      if (typeof bound !== "number" || !Number.isInteger(bound) || bound < 1 || bound > 1000) context.addIssue({ code:"custom",message:"Loop maxIterations must be an integer between 1 and 1000",path:["nodes",index,"configuration","maxIterations"] });
    }
  }
  for (const [index,edge] of graph.edges.entries()) if (!ids.has(edge.source) || !ids.has(edge.target)) context.addIssue({ code:"custom",message:"Edge endpoints must reference existing nodes",path:["edges",index] });
});
export type WorkflowGraph=z.infer<typeof workflowGraphSchema>;

