import { describe,expect,it } from "vitest";
import { workflowGraphSchema } from "./index.js";
describe("workflowGraphSchema",()=>{
  it("accepts a bounded loop",()=>expect(workflowGraphSchema.safeParse({nodes:[{id:"l",type:"loop",version:1,configuration:{maxIterations:10}}],edges:[]}).success).toBe(true));
  it("rejects dangling edges",()=>expect(workflowGraphSchema.safeParse({nodes:[{id:"a",type:"manual",version:1}],edges:[{id:"e",source:"a",target:"missing"}]}).success).toBe(false));
});

