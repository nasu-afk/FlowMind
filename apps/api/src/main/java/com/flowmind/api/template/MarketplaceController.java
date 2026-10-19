package com.flowmind.api.template;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/marketplace/templates")
public class MarketplaceController {
    private final JdbcClient jdbc; private final ObjectMapper json; private final TemplateSanitizer sanitizer=new TemplateSanitizer();
    public MarketplaceController(JdbcClient jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}
    @GetMapping public List<TemplateView> list(@RequestParam(required=false) String category,@RequestParam(required=false) String query){String sql="SELECT t.id,t.slug,t.name,t.description,t.category,t.featured,COALESCE(AVG(r.rating),0) rating,COUNT(r.id) reviews FROM templates t LEFT JOIN template_reviews r ON r.template_id=t.id WHERE t.visibility='PUBLIC' AND t.status='PUBLISHED' AND (:category IS NULL OR t.category=:category) AND (:query IS NULL OR t.name ILIKE '%'||:query||'%' OR t.description ILIKE '%'||:query||'%') GROUP BY t.id ORDER BY t.featured DESC,t.name LIMIT 100";return jdbc.sql(sql).param("category",blankToNull(category)).param("query",blankToNull(query)).query((rs,row)->new TemplateView(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getBoolean(6),rs.getBigDecimal(7),rs.getLong(8))).list();}
    @PostMapping("/{templateId}/install") @ResponseStatus(HttpStatus.CREATED) @Transactional
    public Installed install(@PathVariable UUID templateId,@RequestParam UUID workspaceId,@AuthenticationPrincipal Jwt jwt){UUID actor=UUID.fromString(jwt.getSubject());boolean allowed=jdbc.sql("SELECT EXISTS(SELECT 1 FROM workspace_members WHERE workspace_id=:w AND user_id=:u AND status='ACTIVE' AND role IN ('OWNER','EDITOR'))").param("w",workspaceId).param("u",actor).query(Boolean.class).single();if(!allowed)throw new ResponseStatusException(HttpStatus.FORBIDDEN);var source=jdbc.sql("SELECT t.name,v.graph::text,v.version_number FROM templates t JOIN template_versions v ON v.template_id=t.id WHERE t.id=:id AND t.status='PUBLISHED' AND t.visibility IN ('PUBLIC','UNLISTED') ORDER BY v.version_number DESC LIMIT 1").param("id",templateId).query((rs,row)->new Source(rs.getString(1),rs.getString(2),rs.getInt(3))).optional().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));try{Map<String,Object> graph=json.readValue(source.graph(),json.getTypeFactory().constructMapType(Map.class,String.class,Object.class));sanitizer.requireCredentialFree(graph);UUID workflow=UUID.randomUUID();jdbc.sql("INSERT INTO workflows(id,workspace_id,name,description,owner_id,draft_graph) VALUES (:id,:w,:name,:description,:owner,CAST(:graph AS jsonb))").param("id",workflow).param("w",workspaceId).param("name",source.name()).param("description","Installed from marketplace template").param("owner",actor).param("graph",source.graph()).update();jdbc.sql("INSERT INTO audit_events(workspace_id,actor_id,action,target_type,target_id,metadata) VALUES (:w,:actor,'template.installed','workflow',:target,jsonb_build_object('templateId',CAST(:template AS text),'templateVersion',:version))").param("w",workspaceId).param("actor",actor).param("target",workflow.toString()).param("template",templateId).param("version",source.version()).update();return new Installed(workflow,"DRAFT",false);}catch(IllegalArgumentException unsafe){throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,unsafe.getMessage());}catch(Exception invalid){throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,"Template graph is invalid");}}
    private String blankToNull(String value){return value==null||value.isBlank()?null:value.trim();}
    public record TemplateView(UUID id,String slug,String name,String description,String category,boolean featured,java.math.BigDecimal rating,long reviewCount){}
    public record Installed(UUID workflowId,String status,boolean executed){}
    private record Source(String name,String graph,int version){}
}

