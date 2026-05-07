package com.aichat.app.tools;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 高德地图工具封装
 * REST API 直接调用（后续可替换为 MCP Server）
 */
@Component
@Slf4j
public class AmapTools {

    @Value("${amap.api-key:}")
    private String apiKey;

    public double[] geocode(String address) {
        try {
            String resp = HttpUtil.get("https://restapi.amap.com/v3/geocode/geo",
                    Map.of("key", apiKey, "address", address, "output", "JSON"));
            JSONObject json = JSONUtil.parseObj(resp);
            JSONArray geocodes = json.getJSONArray("geocodes");
            if (geocodes != null && !geocodes.isEmpty()) {
                String location = geocodes.getJSONObject(0).getStr("location");
                String[] parts = location.split(",");
                return new double[]{Double.parseDouble(parts[0]), Double.parseDouble(parts[1])};
            }
        } catch (Exception e) {
            log.error("地理编码失败: {}", address, e);
        }
        return null;
    }

    public List<Map<String, Object>> aroundSearch(double lon, double lat, String keywords, int radius) {
        List<Map<String, Object>> results = new ArrayList<>();
        try {
            String resp = HttpUtil.get("https://restapi.amap.com/v5/place/around",
                    Map.of("key", apiKey, "location", lon + "," + lat,
                            "keywords", keywords, "radius", String.valueOf(radius), "page_size", "5"));
            JSONObject json = JSONUtil.parseObj(resp);
            JSONArray pois = json.getJSONArray("pois");
            if (pois != null) {
                for (int i = 0; i < pois.size(); i++) {
                    JSONObject poi = pois.getJSONObject(i);
                    Map<String, Object> item = new HashMap<>();
                    item.put("name", poi.getStr("name"));
                    item.put("address", poi.getStr("address"));
                    item.put("distance", poi.getStr("distance"));
                    String loc = poi.getStr("location");
                    if (loc != null) {
                        String[] parts = loc.split(",");
                        item.put("longitude", Double.parseDouble(parts[0]));
                        item.put("latitude", Double.parseDouble(parts[1]));
                    }
                    results.add(item);
                }
            }
        } catch (Exception e) {
            log.error("附近搜索失败", e);
        }
        return results;
    }

    public RouteResult walkingRoute(double[] origin, double[] dest) {
        RouteResult result = new RouteResult();
        try {
            String resp = HttpUtil.get("https://restapi.amap.com/v5/direction/walking",
                    Map.of("key", apiKey,
                            "origin", origin[0] + "," + origin[1],
                            "destination", dest[0] + "," + dest[1]));
            JSONObject json = JSONUtil.parseObj(resp);
            JSONArray paths = json.getJSONArray("paths");
            if (paths != null && !paths.isEmpty()) {
                JSONObject path = paths.getJSONObject(0);
                result.setDistance(path.getStr("distance") + "米");
                int seconds = path.getInt("duration");
                result.setDuration((seconds / 60) + "分钟");
            }
        } catch (Exception e) {
            log.error("路线规划失败", e);
        }
        return result;
    }

    @Data
    public static class RouteResult {
        private String distance;
        private String duration;
    }
}
