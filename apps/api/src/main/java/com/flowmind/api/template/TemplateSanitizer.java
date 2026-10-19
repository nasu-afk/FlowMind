package com.flowmind.api.template;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class TemplateSanitizer {
    private static final Set<String> FORBIDDEN=Set.of("password","secret","token","apikey","api_key","authorization","credential","credentialid","credential_id");
    public void requireCredentialFree(Object value){walk(value,"$");}
    private void walk(Object value,String path){
        if(value instanceof Map<?,?> map)for(var entry:map.entrySet()){String key=String.valueOf(entry.getKey());String normalized=key.toLowerCase(Locale.ROOT).replace("-","");if(FORBIDDEN.contains(normalized))throw new IllegalArgumentException("Templates cannot contain credential field: "+path+"."+key);walk(entry.getValue(),path+"."+key);}
        else if(value instanceof List<?> list)for(int i=0;i<list.size();i++)walk(list.get(i),path+"["+i+"]");
    }
}

