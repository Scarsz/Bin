package github.scarsz.bin.route.api.v1;

import github.scarsz.bin.Bin;
import github.scarsz.bin.Server;
import org.apache.commons.lang3.StringUtils;
import spark.Request;
import spark.Response;
import spark.Route;

import java.util.*;

import static spark.Spark.halt;

public class ApiV1PostRoute implements Route {

    @Override
    public Object handle(Request request, Response response) throws Exception {
        if (StringUtils.isBlank(request.body())) return halt(400, "{\"error\":{\"type\":\"BlankBodyException\", \"message\":\"No content received\"}}");
        Map json = Server.getInstance().getGson().fromJson(request.body(), Map.class);
//        System.out.println("input: " + ((Map) ((List) json.get("files")).get(0)).get("type"));

        long expiration = ((long) (double) json.getOrDefault("expiration", (double) Server.getInstance().getConfig().getDefaultExpiration()));
        byte[] description = StringUtils.isNotBlank((String) json.get("description")) ? Base64.getDecoder().decode((String) json.get("description")) : null;

        Bin bin = Bin.create(expiration, description);
        for (Map file : new LinkedList<>((ArrayList<Map>) json.get("files"))) {
            byte[] nameBytes = Base64.getDecoder().decode((String) file.get("name"));
            byte[] contentBytes = Base64.getDecoder().decode((String) file.get("content"));
            String type = String.valueOf(file.getOrDefault("type", "application/octet-stream"));
            bin.addFile(nameBytes, contentBytes, type);
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("status", "ok");
        payload.put("bin", bin.getId());

//        System.out.println("after serialization: " + ((Map) ((List) bin.serialize().get("files")).get(0)).get("name"));

        return Server.getInstance().getGsonPretty().toJson(payload);
    }

}
